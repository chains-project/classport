# classport-agent

This is the Dynamic Analyser. 
It is a Java Agent that intercepts all loaded classes during the runtime execution.

For each intercepted class the annotation is parsed (if it is present).
Finally, Classport creates a flat list (`classport-deps-list`) and a tree (`classport-deps-tree`) of the loaded dependencies. 