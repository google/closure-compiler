/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.javascript.tools.shell;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.ButtonGroup;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.mozilla.javascript.SecurityUtilities;

public class JSConsole extends JFrame implements ActionListener
{
    static final long serialVersionUID = 2551225560631876300L;

    private File CWD;
    private JFileChooser dlg;
    private ConsoleTextArea consoleTextArea;

    public String chooseFile() {
        if(CWD == null) {
            String dir = SecurityUtilities.getSystemProperty("user.dir");
            if(dir != null) {
                CWD = new File(dir);
            }
        }
        if(CWD != null) {
            dlg.setCurrentDirectory(CWD);
        }
        dlg.setDialogTitle("Select a file to load");
        int returnVal = dlg.showOpenDialog(this);
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            String result = dlg.getSelectedFile().getPath();
            CWD = new File(dlg.getSelectedFile().getParent());
            return result;
        }
        return null;
    }

    public static void main(String args[]) {
        new JSConsole(args);
    }

    public void createFileChooser() {
        dlg = new JFileChooser();
        javax.swing.filechooser.FileFilter filter =
            new javax.swing.filechooser.FileFilter() {
                   @Override
                    public boolean accept(File f) {
                        if(f.isDirectory()) {
                            return true;
                        }
                        String name = f.getName();
                        int i = name.lastIndexOf('.');
                        if(i > 0 && i < name.length() -1) {
                            String ext = name.substring(i + 1).toLowerCase();
                            if(ext.equals("js")) {
                                return true;
                            }
                        }
                        return false;
                    }

                   @Override
                    public String getDescription() {
                        return "JavaScript Files (*.js)";
                    }
                };
        dlg.addChoosableFileFilter(filter);

    }

    public JSConsole(String[] args) {
        super("Rhino JavaScript Console");
        JMenuBar menubar = new JMenuBar();
        createFileChooser();
        String[] fileItems  = {"Load...", "Exit"};
        String[] fileCmds  = {"Load", "Exit"};
        char[] fileShortCuts = {'L', 'X'};
        String[] editItems = {"Cut", "Copy", "Paste"};
        char[] editShortCuts = {'T', 'C', 'P'};
        String[] plafItems = {"Metal", "Windows", "Motif"};
        boolean [] plafState = {true, false, false};
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic('F');
        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic('E');
        JMenu plafMenu = new JMenu("Platform");
        plafMenu.setMnemonic('P');
        for(int i = 0; i < fileItems.length; ++i) {
            JMenuItem item = new JMenuItem(fileItems[i],
                                           fileShortCuts[i]);
            item.setActionCommand(fileCmds[i]);
            item.addActionListener(this);
            fileMenu.add(item);
        }
        for(int i = 0; i < editItems.length; ++i) {
            JMenuItem item = new JMenuItem(editItems[i],
                                           editShortCuts[i]);
            item.addActionListener(this);
            editMenu.add(item);
        }
        ButtonGroup group = new ButtonGroup();
        for(int i = 0; i < plafItems.length; ++i) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(plafItems[i],
                                                                 plafState[i]);
            group.add(item);
            item.addActionListener(this);
            plafMenu.add(item);
        }
        menubar.add(fileMenu);
        menubar.add(editMenu);
        menubar.add(plafMenu);
        setJMenuBar(menubar);
        consoleTextArea = new ConsoleTextArea(args);
        JScrollPane scroller = new JScrollPane(consoleTextArea);
        setContentPane(scroller);
        consoleTextArea.setRows(24);
        consoleTextArea.setColumns(80);
        addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    System.exit(0);
                }
            });
        pack();
        setVisible(true);
        // System.setIn(consoleTextArea.getIn());
        // System.setOut(consoleTextArea.getOut());
        // System.setErr(consoleTextArea.getErr());
        Main.setIn(consoleTextArea.getIn());
        Main.setOut(consoleTextArea.getOut());
        Main.setErr(consoleTextArea.getErr());
        Main.main(args);
    }

    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        String plaf_name = null;
        if(cmd.equals("Load")) {
            String f = chooseFile();
            if(f != null) {
                f = f.replace('\\', '/');
                consoleTextArea.eval("load(\"" + f + "\");");
            }
        } else if(cmd.equals("Exit")) {
            System.exit(0);
        } else if(cmd.equals("Cut")) {
            consoleTextArea.cut();
        } else if(cmd.equals("Copy")) {
            consoleTextArea.copy();
        } else if(cmd.equals("Paste")) {
            consoleTextArea.paste();
        } else {
            if(cmd.equals("Metal")) {
                plaf_name = "javax.swing.plaf.metal.MetalLookAndFeel";
            } else if(cmd.equals("Windows")) {
                plaf_name = "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";
            } else if(cmd.equals("Motif")) {
                plaf_name = "com.sun.java.swing.plaf.motif.MotifLookAndFeel";
            }
            if(plaf_name != null) {
                try {
                    UIManager.setLookAndFeel(plaf_name);
                    SwingUtilities.updateComponentTreeUI(this);
                    consoleTextArea.postUpdateUI();
                    // updateComponentTreeUI seems to mess up the file
                    // chooser dialog, so just create a new one
                    createFileChooser();
                } catch(Exception exc) {
                    JOptionPane.showMessageDialog(this,
                                                  exc.getMessage(),
                                                  "Platform",
                                                  JOptionPane.ERROR_MESSAGE);
                }
            }
        }

    }

}
