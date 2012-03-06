//package edu.umich.soar;
//
//import java.io.IOException;
//
//import sml.Agent;
//import sml.Kernel;
//import sml.Kernel.SystemEventInterface;
//import sml.Kernel.UpdateEventInterface;
//import sml.smlSystemEventId;
//import sml.smlUpdateEventId;
//
//public class DataCollectorExample 
//{
//	public static void main(String[] args) throws IOException
//	{
//	    // Create kernel and agent
//        Kernel kernel = sml.Kernel.CreateKernelInNewThread();
//        final Agent agent = kernel.CreateAgent("soar");
//        
//        // Use SoarProperties to find some demo code
//        SoarProperties sp = new SoarProperties();
//        StringBuilder productions = new StringBuilder(sp.getPrefix());
//        productions.append("share/soar/Demos/arithmetic/arithmetic.soar");
//        
//        // TODO: Need a custom agent that generates some more interesting stats
//        if (!agent.LoadProductions(productions.toString()))
//        {
//            System.err.println("Failed to load: " + productions.toString());
//            return;
//        }
//        
//        final DataCollector dc = new DataCollector();
//        
//        // Writing output straight to the screen
//        dc.setOutputStream(System.out);
//        
//        final SystemEventInterface startHandler = new SystemEventInterface()
//        {
//            @Override
//            public void systemEventHandler(int eventID, Object data, Kernel kernel)
//            {
//                // Call onStart when the system starts, starts the wall clock
//                dc.onStart();
//            }
//        };
//        
//        final SystemEventInterface stopHandler = new SystemEventInterface()
//        {
//            @Override
//            public void systemEventHandler(int eventID, Object data, Kernel kernel)
//            {
//                // Call onStop to stop the clock
//                dc.onStop();
//                // Collect data a final time, remember to call it for each agent.
//                // Collect will auto flush if the clock is not running.
//                dc.collect(agent);
//            }
//        };
//        
//        final UpdateEventInterface updateHandler = new UpdateEventInterface()
//        {
//            @Override
//            public void updateEventHandler(int eventID, Object data,
//                    Kernel kernel, int runFlags)
//            {
//                // Call onUpdateEvent once (for all agents) per cycle so
//                // it can keep track of decision cycles
//                if (dc.onUpdateEvent())
//                {
//                    // Call collect when onUpdateEvent returns true
//                    // to collect the data. Call for each agent.
//                    dc.collect(agent);
//                }
//                
//                // Might want to call flush in a larger period than the collection
//                // to guard against catastrophic failures on long runs.
//            }
//        };
//        
//        // To recap:
//        //  * call onStart on system start
//        //  * call onStop on system stop, collect one last time
//        //  * call onUpdateEvent on update event, and, if it returns true, collect for each agent
//        kernel.RegisterForSystemEvent(smlSystemEventId.smlEVENT_SYSTEM_START, startHandler, null);
//        kernel.RegisterForSystemEvent(smlSystemEventId.smlEVENT_SYSTEM_STOP, stopHandler, null);
//        kernel.RegisterForUpdateEvent(smlUpdateEventId.smlEVENT_AFTER_ALL_OUTPUT_PHASES, updateHandler, null);
//		
//        // To collect every n decision cycles, set the period
//        dc.setPeriodCycles(10);
//        System.out.println("Output (100 cycles every 10):");
//        agent.RunSelf(100);
//
//        // Reset reinitializes a lot of intermediate stats such as the clock.
//        // It does not start a new file or print a new header line.
//        dc.reset();
//        System.out.println();
//        
//        // To start a new file, be sure to create a new stream and hand it to the
//        // data collector using dc.setOutputStream(). Doing this will create a new
//        // header line. Note that you still have to call reset if you want to reset
//        // the clock and other statistics.
//        
//        // To collect every n milliseconds, set the period
//        dc.setPeriodMillis(500);
//        agent.InitSoar();
//        System.out.println("Output (forever every .5 sec):");
//        agent.RunSelfForever();
//        
//        kernel.Shutdown();
//        kernel.delete();
//	}
//}
