package io.github.chains_project.classport.commons;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

public class ClassportProject {
    private static final String SBOM_NESTING_DELIM = "|  ";
    private static final String SBOM_ITEM_SPECIFIER = "+-";
    private static final String SBOM_LAST_ITEM_SPECIFIER = "\\-";

    private ArrayList<SBOMNode> directDependencies;

    public ClassportProject(Map<String, ClassportInfo> sbom) {
        List<ClassportInfo> directDeps = sbom.values().stream()
                .filter(dep -> dep.isDirectDependency())
                .collect(Collectors.toList());

        directDependencies = new ArrayList<SBOMNode>();
        for (ClassportInfo dep : directDeps) {
            if (sbom.containsKey(dep.id()))
                directDependencies.add(new SBOMNode(dep.id(), sbom));
        }
    }

    public void writeTree(Writer out) {
        for (int i = 0; i < directDependencies.size(); ++i) {
            SBOMNode dep = directDependencies.get(i);
            // We're at nest level one for each direct dependency
            dep._writeTree(out, 1, i == (directDependencies.size() - 1));
        }

        // Make sure everything's written properly
        try {
            out.flush();
        } catch (

        IOException e) {
            System.err.println("Unable to flush output stream");
        }
    }

    class SBOMNode {
        private String dependencyId;
        private Map<String, ClassportInfo> sbom;
        private List<String> childIds;
        private List<SBOMNode> childNodes;

        public SBOMNode(String dependencyId, Map<String, ClassportInfo> sbom) throws NoSuchElementException {
            this.dependencyId = dependencyId;
            this.sbom = sbom;

            // The dependency will not be present in the SBOM if no class has been loaded
            // from it during runtime
            if (sbom.containsKey(dependencyId))
                this.childIds = Arrays.asList(sbom.get(dependencyId).childIds());
            else
                throw new NoSuchElementException("Dependency " + dependencyId + " appears to be unused");

            childNodes = new ArrayList<SBOMNode>();
            this.buildGraph();
        }

        private void buildGraph() {
            for (String childId : childIds) {
                // If the childId is not in the SBOM, it has not been used and can be omitted
                if (sbom.containsKey(childId)) {
                    childNodes.add(new SBOMNode(childId, sbom));
                }
            }
        }

        public String getId() {
            return dependencyId;
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

                out.write(dependencyId);
                out.write('\n');
                for (int i = 0; i < childNodes.size(); ++i) {
                    SBOMNode dep = childNodes.get(i);
                    dep._writeTree(out, nestLevel + 1, (i == childNodes.size() - 1));
                }
            } catch (Throwable e) {
                System.err.println("Unable to write SBOM for node " + this.dependencyId + ": " + e.getMessage());
            }
        }
    }
}
