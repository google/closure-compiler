var __terminate_interpreter__ = new Continuation();
java.lang.System.out.println("top");
var c;

function fib(x) {
    c = getContinuation();
    java.lang.System.out.println("fib(" + x + "); c = "+c);
    if(c != null) {
        java.lang.System.out.println("here");
    	this.__terminate_interpreter__(null);
    }
    return x < 2 ? 1 : (fib(x-1) + fib(x-2));
}

function getContinuation() {
	return new Continuation();
}

java.lang.System.out.println(fib(3));