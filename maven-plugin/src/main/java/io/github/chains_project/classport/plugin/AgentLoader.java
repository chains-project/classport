package io.github.chains_project.classport.plugin;

import com.sun.tools.attach.VirtualMachine;
import sun.tools.attach.HotSpotVirtualMachine;

import java.io.InputStream;
import java.util.Scanner;

// we don't need this because we can call
// jcmd $(jps | grep "test-agent-app-1.0-SNAPSHOT.jar" | awk '{print $1}') JVMTI.agent_load /home/aman/Desktop/master-thesis/classport/native-agent/agent.so
public class AgentLoader {
    public static void main(String[] args) {
        try {
            // Please change the PID to the PID of the target JVM
            // Use jps to get the PID
            VirtualMachine vm = VirtualMachine.attach("34347");
            vm.loadAgentPath("/home/aman/Desktop/master-thesis/classport/native-agent/agent.so");
//            InputStream in = ((HotSpotVirtualMachine)vm).executeCommand("threaddump", args);
//            // read to EOF and just print output
//            Scanner s = new Scanner(in).useDelimiter("\\A");
//            String result = s.hasNext() ? s.next() : "";
//            System.out.println(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}