/*
 * Copyright 2018 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.javascript.jscomp;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
import com.google.common.io.CharStreams;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.zip.ZipFile;

/**
 * A class that abstract entries from zip files via managed caching.
 *
 * <p>Normally zip files created via URL connection cache indefinite by JDK. That's not good since a
 * zip file contents might change over time (e.g. compiler running as a worker). This class provides
 * a timestamp controlled caching which ensures we always read up-to-date zip while avoiding wasting
 * time by re-reading the zip for each entry.
 */
@GwtIncompatible("java.util.zip.ZipFile")
final class ZipEntryReader implements Serializable {

  private static final long serialVersionUID = 1L;

  private static final int ZIP_CACHE_SIZE =
      Integer.parseInt(System.getProperty("jscomp.zipfile.cachesize", "1000"));

  private static final LoadingCache<String, CachedZipFile> zipFileCache =
      CacheBuilder.newBuilder()
          .maximumSize(ZIP_CACHE_SIZE)
          .removalListener(
              (RemovalNotification<String, CachedZipFile> notification) -> {
                try {
                  notification.getValue().maybeClose();
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              })
          .build(
              new CacheLoader<String, CachedZipFile>() {
                @Override
                public CachedZipFile load(String key) throws IOException {
                  return new CachedZipFile(key);
                }
              });

  private static class CachedZipFile {
    private final Path path;
    private ZipFile zipFile;
    private volatile FileTime lastModified;

    private CachedZipFile(String zipName) {
      this.path = Paths.get(zipName);
    }

    private Reader getReader(String entryName, Charset charset) throws IOException {
      refreshIfNeeded();
      return new InputStreamReader(zipFile.getInputStream(zipFile.getEntry(entryName)), charset);
    }

    private void refreshIfNeeded() throws IOException {
      FileTime newLastModified = Files.getLastModifiedTime(path);
      if (newLastModified.equals(lastModified)) {
        return;
      }

      synchronized (this) {
        // Since we do double checked locking (newLastModified is checked out of synchronized), we
        // should test the stamp again.
        if (newLastModified.equals(lastModified)) {
          return;
        }

        maybeClose();
        zipFile = new ZipFile(path.toFile());
        lastModified = newLastModified;
      }
    }

    private void maybeClose() throws IOException {
      if (zipFile != null) {
        zipFile.close();
      }
    }
  }

  private final String zipPath;
  private final String entryName;

  public ZipEntryReader(String zipPath, String entryName) {
    this.zipPath = zipPath;
    this.entryName = entryName;
  }

  public Reader getReader(Charset charset) throws IOException {
    CachedZipFile zipFile = zipFileCache.getUnchecked(zipPath);
    return new BufferedReader(zipFile.getReader(entryName, charset));
  }

  public String read(Charset charset) throws IOException {
    CachedZipFile zipFile = zipFileCache.getUnchecked(zipPath);
    return CharStreams.toString(zipFile.getReader(entryName, charset));
  }
}
