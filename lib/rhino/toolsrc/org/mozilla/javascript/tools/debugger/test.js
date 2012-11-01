/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */


// Test file for Rhino debugger 



var x = 12;

function sleep(millis)
{
	java.lang.Thread.sleep(millis);
}

function thread_loop_body(counter, thread_title)
{
	print("["+counter+"] Thread '"+thread_title);
	sleep(1000);
}

function make_thread(thread_title, repeat_count, loop_body)
{
	var Thread = java.lang.Thread;
	var thread = new Thread(function() {
		for (var i = 0; i < repeat_count; ++i) {
			loop_body(i, thread_title);
		}
		print("[DONE] Thread "+thread_title);
	});
	return thread;
}

function make_thread2(thread_title, repeat_count, loop_body, loop_end)
{
	var Thread = java.lang.Thread;
	var thread = new Thread(function() {
		create_gui();
		for (var i = 0; i < repeat_count; ++i) {
			loop_body(i, thread_title);
		}
		loop_end(thread_title);
	});
	return thread;
}


var loop_body_text = thread_loop_body.toSource(0);
var loop_body;
eval("loop_body = "+loop_body_text);

var thread1 = make_thread("A", 5, loop_body);
var thread2 = make_thread("B", 1000, loop_body);
var thread3 = make_thread2("C", 2, loop_body, 
		function loop_end(thread_title) {
			print("[DONE] Thread "+thread_title);
			// Do somethig to throw exception
			Math.xxxx();
	        });

thread1.start();
thread2.start();
thread3.start();

thread1.join();
thread2.join();
thread3.join();


function create_gui()
{
    var swing = Packages.javax.swing;
    var awt = Packages.java.awt;
    
    var frame = new swing.JFrame("SwingApplication");
    var labelPrefix = "Number of button clicks: ";
    var numClicks = 0;
    var label = new swing.JLabel(labelPrefix + numClicks);
    var button = new swing.JButton("Click Me!");
    button.mnemonic = Packages.java.awt.event.KeyEvent.VK_I;
    button.addActionListener(function() {
	numClicks += 1;
	label.setText(labelPrefix + numClicks);
    });
    label.setLabelFor(button);

    var pane = new swing.JPanel();
    pane.setLayout(new awt.GridLayout(0, 1));
    pane.add(button);
    pane.add(label);

    frame.getContentPane().add(pane, awt.BorderLayout.CENTER);
    frame.addWindowListener(function(event, methodName) {
    	print(event + " "+methodName);
	if (methodName == "windowClosing") {     
            //java.lang.System.exit(0);
	}
    });

    //Finish setting up the frame, and show it.
    frame.pack();
    frame.setVisible(true);

}

