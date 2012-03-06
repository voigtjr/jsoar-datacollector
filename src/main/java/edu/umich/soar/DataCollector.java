package edu.umich.soar;
/*
 * Copyright (c) 2011, Regents of the University of Michigan
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoar.kernel.Agent;
import org.jsoar.kernel.SoarProperties;
import org.jsoar.kernel.smem.SemanticMemory;
import org.jsoar.kernel.smem.SemanticMemoryStatistics;
import org.jsoar.util.properties.PropertyManager;

/**
 * <p>
 * DataCollector collects statistics on Soar agents as they execute. This can be
 * a tricky affair because of subtle performance gotchas when dealing with the
 * Soar/SML application stack.
 * 
 * <p>
 * Basic usage of this class involves calling methods during three kernel
 * events: system start, system stop, and update (after all output phases).
 * These event interfaces aren't implemented directly by this class so that it
 * is as flexible as possible when introducing it to an existing code base and
 * also so that these calls can be added to existing callbacks instead of
 * creating new ones for it -- callbacks can be very expensive.
 * 
 * <p>
 * Actual data collection can be performed whenever the user likes by calling
 * the collect method to add another row to the data table. Most often these
 * calls to collect are managed by the return value of onUpdateEvent. If
 * onUpdateEvent returns true, it indicates that collect needs to be called for
 * each agent. Note that, even if its return value is ignored, it must still be
 * called each decision cycle.
 * 
 * <p>
 * The behavior of onUpdateEvent can be configured by the user in two different
 * ways for the specific needs of the data collection: by decision cycle period
 * or wall clock time period. To collect data every n decision cycles,
 * setPeriodCycles is called with the desired period. To collect data every n
 * milliseconds, setPeriodMillis is called with the desired period.
 * 
 * <p>
 * The data is collected in a simple csv format written to an output stream of
 * the user's choice. Nothing is set by default so no data is collected until
 * setOutputStream is called with a valid output stream. The first line will be
 * a csv list of column headers (see below). The first line of data will also
 * include a value for the "settings" column, and it will be various agent
 * configuration information relevant to the data being collected. This settings
 * data is very helpful for sanity checks of the data, and it also includes a
 * timestamp.
 * 
 * <p>
 * Flushing the data to the stream can be very expensive, so it is not called
 * during a run. The stream is flushed automatically during onStop and any call
 * to collect after that until onStart is called.
 * 
 * <p>
 * Data columns collected:
 * <table>
 * <tr>
 * <th>Header<th>Description and Units</th>
 * <th>Notes</th>
 * </td>
 * <tr><td>agent</td><td>string name</td>
 * <td></td>
 * </tr>
 * <tr><td>wall clock</td><td>float seconds</td>
 * <td>time stamp for each data collection</td>
 * </tr>
 * <tr><td>dc num</td><td>decision cycle number</td>
 * <td>decision cycle of the collection</td>
 * </tr>
 * <tr><td>kernel msec</td><td>kernel cpu time milliseconds</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>avg msec/dc</td><td>average amount of kernel time milliseconds per decision cycle</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>cpu msec</td><td>cpu time milliseconds</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>pf total</td><td>Productions fired since last data collection</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>average msec/pf</td><td>average number of milliseconds per production firing</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>wm current</td><td>current working memory size</td>
 * <td></td>
 * </tr>
 * <tr><td>wm mean</td><td>mean working memory size (wme count)</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>wm max</td><td>maximum working memory size (wme count)</td>
 * <td></td>
 * </tr>
 * <tr><td>wm additions</td><td>number of working memory element additions</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>wm removals</td><td>number of working memory element removals</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>max dc time cycle</td><td>the cycle number that reported the longest delay</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>max dc time value</td><td>the time value of the delay of the longest cycle</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>max dc changes cycle</td><td>the cycle number that reported the most wme changes</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>max dc changes value</td><td>the number of wme changes reported by the max cycle</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>max dc pf cycle</td><td>the cycle number that reported the most production firings</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>max dc pf value</td><td>the number of production firings reported by the max cycle</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>epmem time</td><td>time spent in episodic memory in milliseconds</td>
 * <td></td>
 * </tr>
 * <tr><td>epmem max time cycle</td><td>the cycle number that reported the most time spent in epmem</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>epmem max time value</td><td>the value of the time spent by the max cycle in milliseconds</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>epmem and smem bytes</td><td>amount of memory used by epmem and smem</td>
 * <td></td>
 * </tr>
 * <tr><td>epmem queries</td><td>epmem queries</td>
 * <td></td>
 * </tr>
 * <tr><td>epmem stores</td><td>number of epmem stores</td>
 * <td></td>
 * </tr>
 * <tr><td>epmem time per dc</td><td>average time spent in epmem per decision cycle in milliseconds</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>smem time</td><td>time spent in semantic memory in milliseconds</td>
 * <td></td>
 * </tr>
 * <tr><td>smem max time cycle</td><td>the cycle number that reported the most time spent in smem</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>smem max time value</td><td>the value of the time spent by the max cycle in milliseconds</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * <tr><td>smem retrieves</td><td>smem retrieval count</td>
 * <td></td>
 * </tr>
 * <tr><td>smem queries</td><td>smem query count</td>
 * <td></td>
 * </tr>
 * <tr><td>smem stores</td><td>number of smem stores</td>
 * <td></td>
 * </tr>
 * <tr><td>smem time per dc</td><td>average time spent in smem per decision cycle in milliseconds</td>
 * <td>resets on each call to collect</td>
 * </tr>
 * </table>
 * 
 * @author Jonathan Voigt <voigtjr@gmail.com>
 */
public class DataCollector
{
    private static final Log logger = LogFactory.getLog(DataCollector.class);
    private static final String HEADER;
    private static final String FORMAT;
    
    private enum DataCollectionMode
    {
        DECISION_CYCLES,
        ELAPSED_TIME,
    }
    
    private DataCollectionMode mode = DataCollectionMode.DECISION_CYCLES;
    private int periodCycles = 5000;
    private int periodMillis;
    private String additionalSettings;

    private OutputStream out = null;
    private PrintWriter pout;
    
    private int count;              // reinitialized in reset()
    private double lastKmsecTime;   // reinitialized in reset()
    private long lastPfCount;       // reinitialized in reset()
    private long lastWmCount;       // reinitialized in reset()
    private long lastWmAdd;         // reinitialized in reset()
    private long lastWmRem;         // reinitialized in reset()
    private double lastCpumsecTime; // reinitialized in reset()
    private double lastSmemTimeMsec;    // reinitialized in reset()
    // TODO private double lastEpmemTimeMsec;   // reinitialized in reset()
    private long lastDc;            // reinitialized in reset()
    private long lastTimeMillis;    // reinitialized in reset()
    private long offset;            // reinitialized in reset()
    private long stopTime;          // reinitialized in reset()
    
    static
    {
        StringBuilder headerBuilder = new StringBuilder();
        StringBuilder formatBuilder = new StringBuilder();
        
        addStat(headerBuilder, "agent", formatBuilder, "%s");
        addStat(headerBuilder, "wall clock", formatBuilder, "%f");
        addStat(headerBuilder, "dc num", formatBuilder, "%d");
        addStat(headerBuilder, "kernel msec", formatBuilder, "%f");
        addStat(headerBuilder, "avg msec/dc", formatBuilder, "%f");
        addStat(headerBuilder, "cpu msec", formatBuilder, "%f");
        addStat(headerBuilder, "pf total", formatBuilder, "%d");
        addStat(headerBuilder, "average msec/pf", formatBuilder, "%f");
        addStat(headerBuilder, "wm current", formatBuilder, "%d");
        addStat(headerBuilder, "wm mean", formatBuilder, "%f");
        addStat(headerBuilder, "wm max", formatBuilder, "%d");
        addStat(headerBuilder, "wm additions", formatBuilder, "%d");
        addStat(headerBuilder, "wm removals", formatBuilder, "%d");
        // TODO addStat(headerBuilder, "max dc time cycle", formatBuilder, "%d");
        // TODO addStat(headerBuilder, "max dc time value", formatBuilder, "%d");
        // TODO addStat(headerBuilder, "max dc changes cycle", formatBuilder, "%d");
        // TODO addStat(headerBuilder, "max dc changes value", formatBuilder, "%d");
        // TODO addStat(headerBuilder, "max dc pf cycle", formatBuilder, "%d");
        // TODO addStat(headerBuilder, "max dc pf value", formatBuilder, "%d");
        // TODO addStat(headerBuilder, "epmem time", formatBuilder, "%f");
        // TODO addStat(headerBuilder, "epmem max time cycle", formatBuilder, "%d");
        // TODO addStat(headerBuilder, "epmem max time value", formatBuilder, "%f");
        // TODO addStat(headerBuilder, "epmem and smem bytes", formatBuilder, "%d");
        // TODO addStat(headerBuilder, "epmem queries", formatBuilder, "%d");
        // TODO addStat(headerBuilder, "epmem stores", formatBuilder, "%d");
        // TODO addStat(headerBuilder, "epmem time per dc", formatBuilder, "%f");
        addStat(headerBuilder, "smem time", formatBuilder, "%f");
        addStat(headerBuilder, "smem max time cycle", formatBuilder, "%d");
        addStat(headerBuilder, "smem max time value", formatBuilder, "%f");
        addStat(headerBuilder, "smem retrieves", formatBuilder, "%d");
        addStat(headerBuilder, "smem queries", formatBuilder, "%d");
        addStat(headerBuilder, "smem stores", formatBuilder, "%d");
        addStat(headerBuilder, "smem time per dc", formatBuilder, "%f");
        
        headerBuilder.append("settings");
        
        HEADER = headerBuilder.toString();
        FORMAT = formatBuilder.toString();
    }
    
    private static void addStat(StringBuilder headerBuilder, String header, StringBuilder formatBuilder, String format)
    {
        headerBuilder.append(header);
        headerBuilder.append(",");
        
        formatBuilder.append(format);
        formatBuilder.append(",");
    }

    /**
     * <p>
     * Constructor, default collection mode is by cycle, 5000 cycles.
     */
    public DataCollector()
    {
        reset();
    }
    
    /**
     * <p>
     * Resets the clock, various statistic counters, period counters, etc. Good
     * for use after init-soar.
     */
    public void reset()
    {
        flush();
        count = 0;
        lastKmsecTime = 0;
        lastCpumsecTime = 0;
        lastPfCount = 0;
        lastWmCount = 0;
        lastWmAdd = 0;
        lastWmRem = 0;
        lastSmemTimeMsec = 0;
        // TODO lastEpmemTimeMsec = 0;
        lastDc = 0;
        lastTimeMillis = 0;
        offset = 0;
        stopTime = 0;
    }
    
    /**
     * <p>
     * Add an arbitrary string to the agent settings value in the file. Do not
     * include double quotes.
     * 
     * @param settings
     *            The settings string to add, or null to clear.
     */
    public void setAdditionalSettings(String settings)
    {
        this.additionalSettings = settings;
    }

    /**
     * <p>
     * Set the output stream to write data to.
     * 
     * <p>
     * The stream is wrapped in a PrintWriter and then print() is called to
     * commit data to it. flush is only called in onStop() and in collect() if
     * the agent is not running but should be called more often during long runs
     * to guard against catastrophic failures.
     * 
     * @param out
     *            Target output stream.
     */
    public void setOutputStream(OutputStream out)
    {
        if (this.out != null && this.out.equals(out))
            return;
        this.out = out;
        this.pout = null;
    }

    /**
     * <p>
     * Set the mode to have onUpdateEvent return true every n calls.
     * 
     * @param cycles
     *            Have onUpdateEvent return true every this many calls.
     */
    public void setPeriodCycles(int cycles)
    {
        this.periodCycles = cycles;
        this.mode = DataCollectionMode.DECISION_CYCLES;
    }

    /**
     * <p>
     * Set the mode to have onUpdateEvent return true based on time, every n
     * milliseconds.
     * 
     * @param millis
     *            Have onUpdateEvent return true every this many milliseconds.
     */
    public void setPeriodMillis(int millis)
    {
        this.periodMillis = millis;
        this.mode = DataCollectionMode.ELAPSED_TIME;
    }
    
    /**
     * <p>
     * Start the wall clock. Call on system event start.
     * 
     * <p>
     * This method must be called from the same thread that fired the event.
     */
    public void onStart()
    {
    	offset += System.currentTimeMillis() - stopTime;
    	stopTime = 0;
    }
    
    /**
     * <p>
     * Stop the wall clock and flush the stream. Call on system event stop.
     * 
     * <p>
     * This method must be called from the same thread that fired the event.
     */
    public void onStop()
    {
    	stopTime = System.currentTimeMillis();
    	flush();
    }
    
    /**
     * <p>
     * Call every decision cycle, returns true if it is time to call collect().
     * 
     * <p>
     * Take care to call this once for all agents every decision cycle. The
     * easiest way to do this is to use one of the update events on the kernel,
     * such as smlUpdateEventId.smlEVENT_AFTER_ALL_OUTPUT_PHASES.
     * 
     * <p>
     * If it returns true, call collect for each agent in the kernel.
     * 
     * <p>
     * This method must be called from the same thread that fired the event.
     * 
     * @return true if it is time to call collect for each agent.
     */
    public boolean onUpdateEvent()
    {
        ++count;
        
        if (!isEnabled())
            return false;

        switch (mode)
        {
        case DECISION_CYCLES:
            return count % periodCycles == 0;
                
        case ELAPSED_TIME:
            long now = System.currentTimeMillis();
            if (lastTimeMillis == 0)
            {
                lastTimeMillis = now;
                return false;
            }
            long deltaMillis = now - lastTimeMillis;
            if (logger.isTraceEnabled())
            {
                logger.trace("Delta millis: " + deltaMillis);
            }
            if (deltaMillis >= periodMillis)
            {
                lastTimeMillis += periodMillis;
                return true;
            }
            return false;
        }
        
        // unreachable unless more modes added
        return false;
    }

    /**
     * <p>
     * Collect stats from the agent and write them to the output stream without
     * flushing.
     * 
     * <p>
     * Call this method for each agent in a kernel after a call to onUpdateEvent
     * (called each decision cycle) returns true.
     * 
     * <p>
     * A header line and the agent's current settings are written once the first
     * time this is called.
     * 
     * <p>
     * The data format is a simple comma separated values text document. Opens
     * directly with Microsoft Excel and LibreOffice.
     * 
     * <p>
     * Occasionally call flush() to guard against catastrophic failures.
     * 
     * <p>
     * This method must be called from the same thread that fired the event.
     * 
     * @param agent
     *            The agent to collect data from.
     */
    public void collect(Agent agent)
    {
        if (!isEnabled())
            return;
        
        logger.debug("Collecting data.");
        try {
            PrintWriter pout = this.pout;
            boolean firstPass = pout == null;
            if (firstPass)
            {
                pout = new PrintWriter(out);
                this.pout = pout;
                pout.println(HEADER);
            }
    
            final PropertyManager props = agent.getProperties();
            
            long dc = props.get(SoarProperties.DECISION_PHASES_COUNT);
            long deltaDc = dc - lastDc;
            lastDc = dc;
            if (dc < 1)
                return;
            
            // getKParamStatsKernelCPUTime returns seconds, divide to get msec
            double kmsec = agent.getTotalKernelTimer().getTotalSeconds();
            double deltaKmsecTime = kmsec - lastKmsecTime;
            lastSmemTimeMsec = kmsec;
            double kmsecTotalTimePerDc = deltaDc > 0 ? deltaKmsecTime / deltaDc : 0;
            
            // getKParamStatsTotalCPUTime returns seconds, divide to get msec
            double tmsec = agent.getTotalCpuTimer().getTotalSeconds();
            double deltaCpumsecTime = tmsec - lastCpumsecTime;
            lastCpumsecTime = tmsec;

            long pf = props.get(SoarProperties.PRODUCTION_FIRING_COUNT);
            long deltaPfCount = pf - lastPfCount;
            lastPfCount = pf;
            double meanMsecPerPf = deltaPfCount > 0 ? deltaKmsecTime / deltaPfCount : 0;
                
            long wmcount = agent.getNumWmesInRete();
            
            // We want wmmean reset each call to collect so we can't use the stat
            //double wmmean = response.GetArgFloat(sml.sml_Names.getKParamStatsWmeCountAverage(), 0);
            long deltaWmCount = wmcount - lastWmCount;
            lastWmCount = wmcount;
            double meanWmCountPerDc = deltaDc > 0 ? deltaWmCount / deltaDc : 0;
            
            long wmmax = props.get(SoarProperties.MAX_WM_SIZE);

            long wmaddTotal = props.get(SoarProperties.WME_ADDITION_COUNT);
            long deltaWmAdd = wmaddTotal - lastWmAdd;
            lastWmAdd = wmaddTotal;
            
            long wmremTotal = props.get(SoarProperties.WME_REMOVAL_COUNT);
            long deltaWmRem = wmremTotal - lastWmRem;
            lastWmRem = wmremTotal;
            
            // TODO long maxdctimec = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleTimeCycle(), 0L);
            // TODO long maxdctimev = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleTimeValueUSec(), 0L);
            // TODO long maxdcwmcc = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleWMChangesCycle(), 0L);
            // TODO long maxdcwmcv = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleWMChangesValue(), 0L);
            // TODO long maxdcpfcc = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleFireCountCycle(), 0L);
            // TODO long maxdcpfcv = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleFireCountValue(), 0L);
            
            // TODO long epmemMaxTimeCycle = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleEpMemTimeCycle(), 0);
            // TODO double epmemMaxTimeValueMsec = response.GetArgFloat(sml.sml_Names.getKParamStatsMaxDecisionCycleEpMemTimeValueSec(), 0) * 1000;
            
            // TODO long smemMaxTimeCycle = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleSMemTimeCycle(), 0);
            // TODO double smemMaxTimeValueMsec = response.GetArgFloat(sml.sml_Names.getKParamStatsMaxDecisionCycleSMemTimeValueSec(), 0) * 1000;
            
            // TODO Scanner epmemTimeScanner = new Scanner(agent.ExecuteCommandLine("epmem -t"));
            // epmem and smem timers report seconds
            // TODO double epmemTimeMsec = epmemTimeScanner.skip(".+: ").nextDouble() * 1000;
            
            // TODO Scanner epmemStatsScanner = new Scanner(agent.ExecuteCommandLine("epmem -S"));
            // TODO long epmemStores = epmemStatsScanner.skip(".+: ").nextLong(); // Time == Stores (more or less)
            // TODO epmemStatsScanner.nextLine(); // Time (Stores)
            // TODO epmemStatsScanner.nextLine(); // SQLite version
            // TODO long epmemAndSmemBytes = epmemStatsScanner.skip(".+: ").nextLong(); 
            // TODO epmemStatsScanner.nextLine(); // Bytes
            // TODO epmemStatsScanner.nextLine(); // Memory Highwater
            // TODO long epmemQueries = epmemStatsScanner.skip(".+: ").nextLong(); 

            SemanticMemory smem = (SemanticMemory)agent.getAdapter(SemanticMemory.class);
            SemanticMemoryStatistics smemStats = smem.getStatistics();
            double smemTimeMsec = 0; // TODO JSoar SemanticMemory does not have timers implemented yet
    
            long smemRetrieves = smemStats.getRetrieves();
            long smemQueries = smemStats.getQueries();
            long smemStores = smemStats.getStores();
            
            double wallClock = (System.currentTimeMillis() - offset) / 1000.0;
            
            double deltaSmemTimeMsec = smemTimeMsec - lastSmemTimeMsec;
            lastSmemTimeMsec = smemTimeMsec;
            double smemTimeMsecPerDc = deltaDc > 0 ? deltaSmemTimeMsec / deltaDc : 0;

            // TODO double deltaEpmemTimeMsec = epmemTimeMsec - lastEpmemTimeMsec;
            // TODO lastEpmemTimeMsec = epmemTimeMsec;
            // TODO double epmemTimeMsecPerDc = deltaDc > 0 ? deltaEpmemTimeMsec / deltaDc : 0;
            
            String out = String.format(FORMAT, agent.getName(), wallClock, dc, deltaKmsecTime, kmsecTotalTimePerDc, 
                    deltaCpumsecTime, deltaPfCount, meanMsecPerPf, wmcount, meanWmCountPerDc, wmmax, deltaWmAdd, deltaWmRem,
                    // TODO maxdctimec, maxdctimev, maxdcwmcc, maxdcwmcv, maxdcpfcc, maxdcpfcv,
                    // TODO epmemTimeMsec, epmemMaxTimeCycle, epmemMaxTimeValueMsec, epmemAndSmemBytes, epmemQueries, epmemStores, epmemTimeMsecPerDc, 
                    smemTimeMsec, 
                    // TODO smemMaxTimeCycle, smemMaxTimeValueMsec, 
                    smemRetrieves, smemQueries, smemStores, smemTimeMsecPerDc);
            pout.print(out);
            //System.out.println(out);
            
            if (firstPass)
            {
                pout.print(getSettingsString(agent));
            }
            pout.println();
            
            // flush only if stopped
            if (stopTime != 0)
            {
                pout.flush();
            }

            // TODO agent.ExecuteCommandLine("stats -R"); // reset max stats
        } 
        catch (Throwable e)
        {
            e.printStackTrace();
        }
    }
    
    /**
     * Flush the stream.
     */
    public void flush()
    {
        PrintWriter pout = this.pout;
        if (pout != null)
            pout.flush();
    }

    private String getSettingsString(Agent agent)
    {
        StringBuilder sb = new StringBuilder("\"");

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sb.append(dateFormat.format(new Date()));
        sb.append(",");
        
        sb.append("Learning is " + (agent.getProperties().get(SoarProperties.LEARNING_ON) ? "enabled" : "disabled"));
        sb.append(",");
        
        sb.append("epmem learning ");
        sb.append("off");
        // TODO sb.append(agent.ExecuteCommandLine("epmem -g learning"));
        sb.append(",");

        sb.append("smem learning ");
        SemanticMemory smem = (SemanticMemory)agent.getAdapter(SemanticMemory.class);
        sb.append(smem.smem_enabled() ? "on" : "off");
        // TODO sb.append(",");
        
        // TODO sb.append("epmem exclusions: [");
        // TODO sb.append(agent.ExecuteCommandLine("epmem -g exclusions"));
        // TODO sb.append("]");
        
        if (additionalSettings != null)
        {
            sb.append(",").append(additionalSettings);
        }
        
        sb.append("\"");
        return sb.toString();
    }

    private boolean isEnabled()
    {
        return out != null;
    }
    
}
