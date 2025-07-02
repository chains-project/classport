package io.github.project.classport.commons;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.Collectors;

public class ClassportProject {
    private static final String SBOM_NESTING_DELIM = "|  ";
    private static final String SBOM_ITEM_SPECIFIER = "+-";
    private static final String SBOM_LAST_ITEM_SPECIFIER = "\\-";

    private String projectRootId;
    private Optional<SBOMNode> projectRoot;
    private Map<String, ClassportInfo> sbom;
    // Only initialised if project root is empty
    private ArrayList<SBOMNode> directDependencies;

    // For memoisation
    private HashMap<String, SBOMNode> nodes;

    public ClassportProject(Map<String, ClassportInfo> sbom) {
        this.sbom = sbom;
        this.nodes = new HashMap<>();
        if (sbom.isEmpty()) {
            projectRootId = "<Unknown>";
            directDependencies = new ArrayList<>();
            projectRoot = Optional.empty();
        } else {
            projectRoot = sbom
                    .values().stream()
                    .filter(dep -> dep.id() == dep.sourceProjectId())
                    .findFirst()
                    .map(ann -> new SBOMNode(ann));
            if (projectRoot.isPresent()) {
                projectRoot.get().buildGraph();
            } else {
                List<ClassportInfo> directDeps = sbom
                        .values().stream()
                        .filter(dep -> dep.isDirectDependency())
                        .collect(Collectors.toList());

                directDependencies = new ArrayList<SBOMNode>();
                for (ClassportInfo dep : directDeps) {
                    projectRootId = dep.sourceProjectId();
                    if (sbom.containsKey(dep.id())) {
                        SBOMNode n = new SBOMNode(dep);
                        n.buildGraph();
                        directDependencies.add(n);
                    }
                }
            }
        }
    }

    public void writeTree(Writer out) {
        if (projectRoot.isPresent()) {
            projectRoot.get()._writeTree(out, 0, true);
        } else {
            try {
                out.write(projectRootId + " (parent-only artefact)\n");
            } catch (IOException e) {
                System.err.println("Unable to write source project ID: " + e.getMessage());
            }

            for (int i = 0; i < directDependencies.size(); ++i) {
                SBOMNode dep = directDependencies.get(i);
                // Direct dependencies are considered to be at nest level 1
                dep._writeTree(out, 1, i == (directDependencies.size() - 1));
            }
        }

        // Make sure everything's written properly
        try {
            out.flush();
        } catch (IOException e) {
            System.err.println("Unable to flush output stream");
        }
    }

    class SBOMNode {
        private ClassportInfo meta;
        private List<SBOMNode> childNodes;

        public SBOMNode(ClassportInfo meta) {
            this.meta = meta;
            childNodes = new ArrayList<SBOMNode>();
        }

        /*
         * Builds a graph of dependency nodes according to the Classport metadata.
         * In some cases, dependencies may have their versions changed by Maven (e.g. a
         * dependency depends on version 3.1.5 but another depends on 3.4.4). In this
         * case, only one is packaged and will thus also be used by the other
         * dependency. We account for this by first trying to match on the full ID, and
         * failing that, fall back on matching group + artefact.
         */
        private void buildGraph() {
            buildGraph(new Stack<>());
        }

        private void buildGraph(Stack<String> path) {
            outer: for (String childId : meta.childIds()) {
                // Have we already resolved this node?
                if (nodes.containsKey(childId)) {
                    childNodes.add(nodes.get(childId));
                } else {
                    // Have we already resolved the artefact with another version?
                    // Version will always be last in the ID
                    String childIdWithoutVersion = childId.substring(0, childId.lastIndexOf(":"));

                    for (String usedDepId : nodes.keySet()) {
                        if (usedDepId.contains(childIdWithoutVersion)) {
                            // Maven has packaged another version, reflect this in the child node
                            childNodes.add(nodes.get(usedDepId));
                            continue outer;
                        }
                    }

                    // We have not generated a node for this dependency before.
                    String newNodeId = new String();
                    if (sbom.containsKey(childId)) {
                        // We've used the exact version specified in the deps
                        newNodeId = childId;
                    } else {
                        // `childId` is never used. However, it might just be a version mismatch.
                        for (String usedDepId : sbom.keySet()) {
                            if (usedDepId.contains(childIdWithoutVersion)) {
                                // Maven has packaged another version, reflect this in the new node
                                newNodeId = usedDepId;
                            }
                        }
                    }

                    // If we have a matching dependency which has not appeared
                    // earlier in our traversal, add it
                    if (!newNodeId.isEmpty() && !path.contains(newNodeId)) {
                        SBOMNode n = new SBOMNode(sbom.get(newNodeId));

                        // Trace where we've been to avoid endless recursion for
                        // dependency cycles (see commons-lang3 and commons-text)
                        path.push(newNodeId);
                        n.buildGraph(path);
                        path.pop();

                        childNodes.add(n);
                        nodes.put(newNodeId, n);
                    }
                }
            }
        }

        public void _writeTree(Writer out, int nestLevel, boolean isLast) {
            try {
                // Do some pretty-printing
                if (nestLevel > 0) {
                    for (int i = 0; i < nestLevel - 1; ++i)
                        out.write(SBOM_NESTING_DELIM);

                    if (isLast)
                        out.write(SBOM_LAST_ITEM_SPECIFIER + " ");
                    else
                        out.write(SBOM_ITEM_SPECIFIER + " ");
                }

                out.write(meta.id());
                out.write('\n');
                for (int i = 0; i < childNodes.size(); ++i) {
                    SBOMNode dep = childNodes.get(i);
                    dep._writeTree(out, nestLevel + 1, (i == childNodes.size() - 1));
                }
            } catch (Throwable e) {
                System.err.println("Unable to write SBOM for node " + meta.id() + ": " + e.getMessage());
            }
        }
    }
}
