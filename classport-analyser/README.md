# classport-analyser

This module is for **static analysis** of the JARs.

Classport makes use of the JAR API provided by the Java standard library to walk through its files. The first four bytes of each file is checked to see whether it is a class file or not. For each class file, the Static Analyser attempts to parse the annotation using the ASM library and stores each parsed annotation in a map using its id as the key. Any class files with missing annotations are immediately reported as warnings.

