package io.github.project.classport.commons;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;


public class AnnotationConstantPool {
    private ClassportInfo annotationInfo;

    public AnnotationConstantPool(ClassportInfo annotationInfo) {
        this.annotationInfo = annotationInfo;
    }

    public record ConstantPoolData(int entryCount, byte[] data) {}

    public ConstantPoolData getNewEntries() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            int entryCount = 0;
            // Annotation type descriptor
            writeUtf8Entry(dos, "Lio/github/project/classport/commons/ClassportInfo;");
            entryCount++;

            // Group (key)
            writeUtf8Entry(dos, "group");
            // Group (value)
            writeUtf8Entry(dos, annotationInfo.group());
            entryCount += 2;

            // version (key)
            writeUtf8Entry(dos, "version");
            // version (value)
            writeUtf8Entry(dos, annotationInfo.version());
            entryCount += 2;

            // id (key)
            writeUtf8Entry(dos, "id");
            // id (value)
            writeUtf8Entry(dos, annotationInfo.id());
            entryCount += 2;

            // sourceProjectId (key)
            writeUtf8Entry(dos, "sourceProjectId");
            // sourceProjectId (value)
            writeUtf8Entry(dos, annotationInfo.sourceProjectId());
            entryCount += 2;

            // childIds (key)
            writeUtf8Entry(dos, "childIds");
            entryCount++;
            // childIds (value)
            for (String childId : annotationInfo.childIds()) {
                writeUtf8Entry(dos, childId);
                entryCount++;
            }

            // artefact (key)
            writeUtf8Entry(dos, "artefact");
            // artefact (value)
            writeUtf8Entry(dos, annotationInfo.artefact());
            entryCount += 2;

            // isDirectDependency (key)
            writeUtf8Entry(dos, "isDirectDependency");
            // isDirectDependency (value)
            writeIntegerEntry(dos, annotationInfo.isDirectDependency() ? 1 : 0);
            entryCount += 2;
            return new ConstantPoolData(entryCount, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write annotation constant pool", e);
        }
    }

    private void writeUtf8Entry(DataOutputStream out, String value) throws IOException {
        out.writeByte(1); // CONSTANT_Utf8 tag
        out.writeUTF(value);
    }
    
    private void writeIntegerEntry(DataOutputStream out, int value) throws IOException {
        out.writeByte(3); // CONSTANT_Integer tag
        out.writeInt(value);
    }

    public byte[] injectAnnotation(byte[] originalBytes, ConstantPoolData cpd) {
        ByteBuffer originalBuffer = ByteBuffer.wrap(originalBytes);
        // Two times the size of the original bytes to account for the new entries
        ByteArrayOutputStream out = new ByteArrayOutputStream(originalBytes.length + cpd.data().length*2);
        DataOutputStream dos = new DataOutputStream(out);
        try {
            dos.writeInt(originalBuffer.getInt()); // magic number
            dos.writeShort(originalBuffer.getShort()); // minor version
            dos.writeShort(originalBuffer.getShort()); // major version
            
            // Constant pool count
            int originalConstantPoolCount = originalBuffer.getShort() & 0xFFFF;
            
            int cpStart = originalBuffer.position();
            skipConstantPool(originalBuffer, originalConstantPoolCount - 1);
            int cpEnd = originalBuffer.position();

            byte[] existingCpData = new byte[cpEnd - cpStart];
            originalBuffer.position(cpStart);
            originalBuffer.get(existingCpData);
            
            int rvaUtf8Index = findUtf8InConstantPool(existingCpData, originalConstantPoolCount - 1, "RuntimeVisibleAnnotations");
            boolean rvaExists = rvaUtf8Index > 0;
            
            int newConstantPoolCount = originalConstantPoolCount + cpd.entryCount();
            if (!rvaExists) {
                newConstantPoolCount++; // add one for "RuntimeVisibleAnnotations"
            }
            dos.writeShort(newConstantPoolCount);
            
            dos.write(existingCpData);

            // new entries
            dos.write(cpd.data());
            if (!rvaExists) {
                writeUtf8Entry(dos, "RuntimeVisibleAnnotations");
            }

            dos.writeShort(originalBuffer.getShort()); // access flags
            dos.writeShort(originalBuffer.getShort()); // this_class
            dos.writeShort(originalBuffer.getShort()); // super_class

            int interfaceCount = originalBuffer.getShort() & 0xFFFF;
            dos.writeShort(interfaceCount);
            for (int i = 0; i < interfaceCount; i++) {
                dos.writeShort(originalBuffer.getShort());
            }

            copyMembers(originalBuffer, dos); // fields
            copyMembers(originalBuffer, dos); // methods

                    // Calculate indices for our new entries
            CPIndices indices = new CPIndices(originalConstantPoolCount, rvaExists ? rvaUtf8Index : -1);
            // 6. Handle class attributes (add RuntimeVisibleAnnotations)
            int classAttrCount = originalBuffer.getShort() & 0xFFFF;
            int rvaIndex = findRuntimeVisibleAnnotationsAttribute(originalBytes, originalBuffer.position() - 2, classAttrCount);
            
            if (rvaIndex >= 0) {
                // Existing RuntimeVisibleAnnotations - merge our annotation
                dos.writeShort(classAttrCount); // same count
                
                for (int i = 0; i < classAttrCount; i++) {
                    int nameIndex = originalBuffer.getShort() & 0xFFFF;
                    int attrLen = originalBuffer.getInt();
                    byte[] attrData = new byte[attrLen];
                    originalBuffer.get(attrData);
                    
                    if (i == rvaIndex) {
                        // Merge annotation
                        dos.writeShort(nameIndex);
                        byte[] merged = mergeAnnotation(attrData, indices);
                        dos.writeInt(merged.length);
                        dos.write(merged);
                    } else {
                        // Copy as-is
                        dos.writeShort(nameIndex);
                        dos.writeInt(attrLen);
                        dos.write(attrData);
                    }
                }
            } else {
                // No existing RuntimeVisibleAnnotations - add new attribute
                dos.writeShort(classAttrCount + 1);
                
                // Copy existing attributes
                for (int i = 0; i < classAttrCount; i++) {
                    dos.writeShort(originalBuffer.getShort()); // name
                    int attrLen = originalBuffer.getInt();
                    dos.writeInt(attrLen);
                    byte[] attrData = new byte[attrLen];
                    originalBuffer.get(attrData);
                    dos.write(attrData);
                }
                
                // Add new RuntimeVisibleAnnotations attribute
                dos.writeShort(indices.rvaNameIndex);
                byte[] newRVA = createRuntimeVisibleAnnotations(indices);
                dos.writeInt(newRVA.length);
                dos.write(newRVA);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to write annotation constant pool", e);
        }

        return out.toByteArray();


    }

    private byte[] mergeAnnotation(byte[] existingRVA, CPIndices indices) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(out);
        
        ByteBuffer buffer = ByteBuffer.wrap(existingRVA);
        int annotationCount = buffer.getShort() & 0xFFFF;
        
        // Write increased count
        dataOut.writeShort(annotationCount + 1);
        
        // Copy existing annotations
        byte[] existing = new byte[buffer.remaining()];
        buffer.get(existing);
        dataOut.write(existing);
        
        // Append our annotation
        writeAnnotation(dataOut, indices);
        
        return out.toByteArray();
    }
    
    private byte[] createRuntimeVisibleAnnotations(CPIndices indices) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(out);
        
        // num_annotations = 1
        dataOut.writeShort(1);
        
        writeAnnotation(dataOut, indices);
        
        return out.toByteArray();
    }
    
    private void writeAnnotation(DataOutputStream out, CPIndices indices) throws IOException {
        // annotation {
        //   u2 type_index;
        //   u2 num_element_value_pairs;
        //   element_value_pair[];
        // }
        
        out.writeShort(indices.annotationTypeIndex);
        out.writeShort(7); // 7 element value pairs (group, version, id, sourceProjectId, childIds, artefact, isDirectDependency)
        
        // group
        writeStringElementValue(out, indices.groupNameIndex, indices.groupValueIndex);
        
        // version
        writeStringElementValue(out, indices.versionNameIndex, indices.versionValueIndex);
        
        // id
        writeStringElementValue(out, indices.idNameIndex, indices.idValueIndex);
        
        // sourceProjectId
        writeStringElementValue(out, indices.sourceProjectIdNameIndex, indices.sourceProjectIdValueIndex);
        
        // childIds (array)
        out.writeShort(indices.childIdsNameIndex);
        out.writeByte('['); // array tag
        out.writeShort(annotationInfo.childIds().length); // num_values
        for (int childIdIndex : indices.childIdsValueIndices) {
            out.writeByte('s'); // string tag
            out.writeShort(childIdIndex);
        }
        
        // artefact
        writeStringElementValue(out, indices.artefactNameIndex, indices.artefactValueIndex);
        
        // isDirectDependency 
        out.writeShort(indices.isDirectDependencyNameIndex);
        out.writeByte('Z'); // boolean tag
        out.writeShort(indices.isDirectDependencyValueIndex);
    }
    
    private void writeStringElementValue(DataOutputStream out, int nameIndex, int valueIndex) throws IOException {
        out.writeShort(nameIndex);
        out.writeByte('s'); // string tag
        out.writeShort(valueIndex);
    }

    private int findRuntimeVisibleAnnotationsAttribute(byte[] classBytes, int attrStartPos, int attrCount) {
        ByteBuffer buffer = ByteBuffer.wrap(classBytes);
        buffer.position(attrStartPos);
        buffer.getShort(); // skip count
        
        for (int i = 0; i < attrCount; i++) {
            int nameIndex = buffer.getShort() & 0xFFFF;
            int attrLen = buffer.getInt();
            
            if (getCPString(classBytes, nameIndex).equals("RuntimeVisibleAnnotations")) {
                return i;
            }
            
            buffer.position(buffer.position() + attrLen);
        }
        
        return -1;
    }
    
    private String getCPString(byte[] classBytes, int cpIndex) {
        ByteBuffer buffer = ByteBuffer.wrap(classBytes);
        buffer.position(8); // skip magic + version
        
        int cpCount = buffer.getShort() & 0xFFFF;
        
        // Navigate to the CP entry
        for (int i = 1; i < cpIndex; i++) {
            int tag = buffer.get() & 0xFF;
            
            switch (tag) {
                case 1: // UTF8
                    int len = buffer.getShort() & 0xFFFF;
                    buffer.position(buffer.position() + len);
                    break;
                case 3: case 4:
                    buffer.getInt();
                    break;
                case 5: case 6:
                    buffer.getLong();
                    i++;
                    break;
                case 7: case 8: case 16: case 19: case 20:
                    buffer.getShort();
                    break;
                case 9: case 10: case 11: case 12: case 17: case 18:
                    buffer.getInt();
                    break;
                case 15:
                    buffer.get();
                    buffer.getShort();
                    break;
            }
        }
        
        // Read the UTF8 entry
        if ((buffer.get() & 0xFF) != 1) return "";
        
        int len = buffer.getShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        buffer.get(bytes);
        
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private void copyMembers(ByteBuffer buffer, DataOutputStream out) throws IOException {
        int count = buffer.getShort() & 0xFFFF;
        out.writeShort(count);
        
        for (int i = 0; i < count; i++) {
            out.writeShort(buffer.getShort()); // access flags
            out.writeShort(buffer.getShort()); // name index
            out.writeShort(buffer.getShort()); // descriptor index
            
            int attrCount = buffer.getShort() & 0xFFFF;
            out.writeShort(attrCount);
            
            for (int j = 0; j < attrCount; j++) {
                out.writeShort(buffer.getShort()); // attribute name
                int attrLen = buffer.getInt();
                out.writeInt(attrLen);
                
                byte[] attrData = new byte[attrLen];
                buffer.get(attrData);
                out.write(attrData);
            }
        }
    }

    private int findUtf8InConstantPool(byte[] cpData, int cpCount, String target) {
        ByteBuffer buffer = ByteBuffer.wrap(cpData);
        int currentIndex = 1; // Constant pool indices start at 1
        
        for (int i = 0; i < cpCount; i++) {
            int tag = buffer.get() & 0xFF;
            
            switch (tag) {
                case 1: // UTF8
                    int len = buffer.getShort() & 0xFFFF;
                    byte[] utf8Bytes = new byte[len];
                    buffer.get(utf8Bytes);
                    String utf8String = new String(utf8Bytes, java.nio.charset.StandardCharsets.UTF_8);
                    if (utf8String.equals(target)) {
                        return currentIndex;
                    }
                    break;
                case 3: case 4: // Integer, Float
                    buffer.getInt();
                    break;
                case 5: case 6: // Long, Double
                    buffer.getLong();
                    i++; // takes 2 slots
                    currentIndex++; // Long and Double take 2 constant pool slots
                    break;
                case 7: case 8: case 16: case 19: case 20: // Class, String, etc.
                    buffer.getShort();
                    break;
                case 9: case 10: case 11: case 12: case 17: case 18: // Fieldref, etc.
                    buffer.getInt();
                    break;
                case 15: // MethodHandle
                    buffer.get();
                    buffer.getShort();
                    break;
            }
            currentIndex++;
        }
        
        return -1; // Not found
    }

    private void skipConstantPool(ByteBuffer buffer, int count) {
        for (int i = 0; i < count; i++) {
            int tag = buffer.get() & 0xFF;
            
            switch (tag) {
                case 1: // UTF8
                    int len = buffer.getShort() & 0xFFFF;
                    buffer.position(buffer.position() + len);
                    break;
                case 3: case 4: // Integer, Float
                    buffer.getInt();
                    break;
                case 5: case 6: // Long, Double
                    buffer.getLong();
                    i++; // takes 2 slots
                    break;
                case 7: case 8: case 16: case 19: case 20: // Class, String, etc.
                    buffer.getShort();
                    break;
                case 9: case 10: case 11: case 12: case 17: case 18: // Fieldref, etc.
                    buffer.getInt();
                    break;
                case 15: // MethodHandle
                    buffer.get();
                    buffer.getShort();
                    break;
            }
        }
    }

    private class CPIndices {
        final int annotationTypeIndex;
        final int groupNameIndex;
        final int groupValueIndex;
        final int versionNameIndex;
        final int versionValueIndex;
        final int idNameIndex;
        final int idValueIndex;
        final int sourceProjectIdNameIndex;
        final int sourceProjectIdValueIndex;
        final int childIdsNameIndex;
        final int[] childIdsValueIndices;
        final int artefactNameIndex;
        final int artefactValueIndex;
        final int isDirectDependencyNameIndex;
        final int isDirectDependencyValueIndex;
        final int rvaNameIndex;
        
        CPIndices(int baseCPCount, int existingRvaIndex) {
            int offset = 0;
            
            annotationTypeIndex = baseCPCount + offset++;
            groupNameIndex = baseCPCount + offset++;
            groupValueIndex = baseCPCount + offset++;
            versionNameIndex = baseCPCount + offset++;
            versionValueIndex = baseCPCount + offset++;
            idNameIndex = baseCPCount + offset++;
            idValueIndex = baseCPCount + offset++;
            sourceProjectIdNameIndex = baseCPCount + offset++;
            sourceProjectIdValueIndex = baseCPCount + offset++;
            childIdsNameIndex = baseCPCount + offset++;
            
            childIdsValueIndices = new int[annotationInfo.childIds().length];
            for (int i = 0; i < childIdsValueIndices.length; i++) {
                childIdsValueIndices[i] = baseCPCount + offset++;
            }
            
            artefactNameIndex = baseCPCount + offset++;
            artefactValueIndex = baseCPCount + offset++;
            isDirectDependencyNameIndex = baseCPCount + offset++;
            isDirectDependencyValueIndex = baseCPCount + offset++;
            
            // Use existing index if found, otherwise calculate new index
            if (existingRvaIndex > 0) {
                rvaNameIndex = existingRvaIndex;
            } else {
                rvaNameIndex = baseCPCount + offset;
            }
        }
    }
}
