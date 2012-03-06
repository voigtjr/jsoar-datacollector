package edu.umich.soar;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.jsoar.kernel.Agent;
import org.jsoar.kernel.RunType;
import org.jsoar.kernel.SoarException;
import org.jsoar.kernel.SoarProperties;
import org.jsoar.kernel.events.RunLoopEvent;
import org.jsoar.runtime.ThreadedAgent;
import org.jsoar.util.commands.SoarCommands;
import org.jsoar.util.events.SoarEvent;
import org.jsoar.util.events.SoarEventListener;
import org.jsoar.util.properties.PropertyChangeEvent;
import org.jsoar.util.properties.PropertyListener;
import org.jsoar.util.properties.PropertyManager;

public class DataCollectorExample 
{
	public static void main(String[] args) throws IOException
	{
	    // Create agent.
	    final ThreadedAgent threaded = ThreadedAgent.create();
	    threaded.setName("soar");
	    threaded.getPrinter().pushWriter(new OutputStreamWriter(System.out));	
	    
        // TODO: Need a custom agent that generates some more interesting stats
	    try
        {
            SoarCommands.source(threaded.getInterpreter(), "http://soar.googlecode.com/svn/trunk/Agents/Arithmetic/arithmetic.soar");
        }
        catch (SoarException e1)
        {
            e1.printStackTrace();
            System.exit(1);
        }
        
        final DataCollector dc = new DataCollector();
        
        // Writing output straight to the screen
        dc.setOutputStream(System.out);
        
        final PropertyListener<Boolean> runningHandler = new PropertyListener<Boolean>()
        {
            @Override
            public void propertyChanged(PropertyChangeEvent<Boolean> event)
            {
                if (event.getNewValue())
                {
                    // Call onStart when the system starts, starts the wall clock
                    dc.onStart();
                }
                else
                {
                    // Call onStop to stop the clock
                    dc.onStop();
                    // Collect data a final time, remember to call it for each agent.
                    // Collect will auto flush if the clock is not running.
                    dc.collect(threaded.getAgent());
                }
            }
        };
        
        final SoarEventListener updateHandler = new SoarEventListener()
        {
            @Override
            public void onEvent(SoarEvent event)
            {
                // Call onUpdateEvent once (for all agents) per cycle so
                // it can keep track of decision cycles
                if (dc.onUpdateEvent())
                {
                    // Call collect when onUpdateEvent returns true
                    // to collect the data. Call for each agent.
                    dc.collect(threaded.getAgent());
                }
                
                // Might want to call flush in a larger period than the collection
                // to guard against catastrophic failures on long runs.
            }
        };
        
        // To recap:
        //  * call onStart on system start
        //  * call onStop on system stop, collect one last time
        //  * call onUpdateEvent on update event, and, if it returns true, collect for each agent
        PropertyManager props = threaded.getProperties();
        props.addListener(SoarProperties.IS_RUNNING, runningHandler);
        threaded.getEvents().addListener(RunLoopEvent.class, updateHandler);
		
        // To collect every n decision cycles, set the period
        dc.setPeriodCycles(10);
        System.out.println("Output (100 cycles every 10):");

        final Agent agent = threaded.getAgent();
        try
        {
            threaded.executeAndWait(new Callable<Void>() {
                public Void call() throws Exception {
                   agent.runFor(100, RunType.DECISIONS); // blocks
                   return null;
                }
            }, 60, TimeUnit.SECONDS);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            System.exit(1);
        }
        
        // Reset reinitializes a lot of intermediate stats such as the clock.
        // It does not start a new file or print a new header line.
        dc.reset();
        System.out.println();
        
        // To start a new file, be sure to create a new stream and hand it to the
        // data collector using dc.setOutputStream(). Doing this will create a new
        // header line. Note that you still have to call reset if you want to reset
        // the clock and other statistics.
        
        // To collect every n milliseconds, set the period
        dc.setPeriodMillis(500);
        threaded.initialize();
        System.out.println("Output (forever every .5 sec):");

        threaded.runForever(); // doesn't block
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        
        threaded.dispose();
	}
}
