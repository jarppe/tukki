# tukki - Simple clojure first logging library with SLF4J compatibility

Experimental library for handling logging in Clojure first way.

Most Clojure logging libraries are wrappers to an exiting Java libraries. I was
wondering what a Clojure first solution would look like.

This library is Clojure logging library with SLF4J API for compatibility with
existing Java libraries.

The major differences to existing solutions are:

- Clojure friendly output out of the box
- Write log output to `System/out` leaving REPL (that uses `*out*`) clean
- The main logging API is done with macros that do not emit any code if the
  logging level is lower than configured level
- About 7x faster than logback
- No dependencies

## Clojure friendly output

The default appender function (you can make your own, it's just a function) aims for developer happiness.
It has following defaults:

- Output is colored by log level, gray for debug, green for info, red for errors etc.
- Namespaces are truncated so that `foo.bar.boz` becomes `f.b.boz`
- Exceptions are printed with the root cause first
- Only the root cause stack trace is printed completely
- Stacktraces print clojure names as they are found in clojure files, so that function `hello!` is
  printed as `hello!`, instead if `hello_BANG_`
