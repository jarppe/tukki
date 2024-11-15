# tukki - Simple clojure first logging library with SLF4J compatibility

Experimental library for handling logging in Clojure first way.

Most Clojure logging libraries are wrappers to an exiting Java libraries. I was
wondering what a Clojure first solution would look like.

This library is Clojure logging library with SLF4J API for compatibility with
existing Java libraries.

The major differences to existing solutions are:

- No dependencies
- The main logging API is done with macros that do not emit any code if the
  logging level is lower than configured level
