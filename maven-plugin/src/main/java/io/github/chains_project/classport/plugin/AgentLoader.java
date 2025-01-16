package io.github.chains_project.classport.plugin;

import com.sun.tools.attach.VirtualMachine;
import sun.tools.attach.HotSpotVirtualMachine;

import java.io.InputStream;

public class AgentLoader {
    public static void main(String[] args) {
        try {
            // Please change the PID to the PID of the target JVM
            // Use jps to get the PID
            VirtualMachine vm = VirtualMachine.attach("31132");
            vm.loadAgentPath("/home/aman/Desktop/master-thesis/classport/native-agent/agent.so");
            InputStream in = ((HotSpotVirtualMachine)vm).executeCommand("threaddump", args);
            // read to EOF and just print output
//            Scanner s = new Scanner(in).useDelimiter("\\A");
//            String result = s.hasNext() ? s.next() : "";
//            System.out.println(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}