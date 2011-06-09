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
import java.util.Scanner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sml.Agent;
import sml.ClientAnalyzedXML;

/**
 * @author voigtjr@gmail.com
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
    private double lastSmemTime;    // reinitialized in reset()
    private double lastEpmemTime;   // reinitialized in reset()
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
        addStat(headerBuilder, "kernel sec", formatBuilder, "%f");
        addStat(headerBuilder, "avg msec/dc", formatBuilder, "%f");
        addStat(headerBuilder, "cpu sec", formatBuilder, "%f");
        addStat(headerBuilder, "pf total", formatBuilder, "%d");
        addStat(headerBuilder, "average msec/pf", formatBuilder, "%f");
        addStat(headerBuilder, "wm current", formatBuilder, "%d");
        addStat(headerBuilder, "wm mean", formatBuilder, "%f");
        addStat(headerBuilder, "wm max", formatBuilder, "%d");
        addStat(headerBuilder, "wm additions", formatBuilder, "%d");
        addStat(headerBuilder, "wm removals", formatBuilder, "%d");
        addStat(headerBuilder, "max dc time cycle", formatBuilder, "%d");
        addStat(headerBuilder, "max dc time value", formatBuilder, "%d");
        addStat(headerBuilder, "max dc changes cycle", formatBuilder, "%d");
        addStat(headerBuilder, "max dc changes value", formatBuilder, "%d");
        addStat(headerBuilder, "max dc pf cycle", formatBuilder, "%d");
        addStat(headerBuilder, "max dc pf value", formatBuilder, "%d");
        addStat(headerBuilder, "epmem time", formatBuilder, "%f");
        addStat(headerBuilder, "epmem max time cycle", formatBuilder, "%d");
        addStat(headerBuilder, "epmem max time value", formatBuilder, "%f");
        addStat(headerBuilder, "epmem bytes", formatBuilder, "%d");
        addStat(headerBuilder, "epmem stores", formatBuilder, "%d");
        addStat(headerBuilder, "epmem time per dc", formatBuilder, "%f");
        addStat(headerBuilder, "smem time", formatBuilder, "%f");
        addStat(headerBuilder, "smem max time cycle", formatBuilder, "%d");
        addStat(headerBuilder, "smem max time value", formatBuilder, "%f");
        addStat(headerBuilder, "smem bytes", formatBuilder, "%d");
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
        lastSmemTime = 0;
        lastEpmemTime = 0;
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
    
            ClientAnalyzedXML response = new ClientAnalyzedXML();
            agent.ExecuteCommandLineXML("stats", response);
            
            long dc = response.GetArgInt(sml.sml_Names.getKParamStatsCycleCountDecision(), 0L);
            long deltaDc = dc - lastDc;
            lastDc = dc;
            if (dc < 1)
                return;
            double ksec = response.GetArgFloat(sml.sml_Names.getKParamStatsKernelCPUTime(), 0);
            double tsec = response.GetArgFloat(sml.sml_Names.getKParamStatsTotalCPUTime(), 0);
            long pf = response.GetArgInt(sml.sml_Names.getKParamStatsProductionFiringCount(), 0L);
            long wmcount = response.GetArgInt(sml.sml_Names.getKParamStatsWmeCount(), 0L);
            double wmmean = response.GetArgFloat(sml.sml_Names.getKParamStatsWmeCountAverage(), 0);
            long wmmax = response.GetArgInt(sml.sml_Names.getKParamStatsWmeCountMax(), 0L);
            
            long wmadd = response.GetArgInt(sml.sml_Names.getKParamStatsWmeCountAddition(), 0L);
            long wmrem = response.GetArgInt(sml.sml_Names.getKParamStatsWmeCountRemoval(), 0L);
            
            long maxdctimec = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleTimeCycle(), 0L);
            long maxdctimev = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleTimeValueUSec(), 0L);
            long maxdcwmcc = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleWMChangesCycle(), 0L);
            long maxdcwmcv = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleWMChangesValue(), 0L);
            long maxdcpfcc = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleFireCountCycle(), 0L);
            long maxdcpfcv = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleFireCountValue(), 0L);
            
            long epmemMaxTimeCycle = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleEpMemTimeCycle(), 0);
            double epmemMaxTimeValue = response.GetArgFloat(sml.sml_Names.getKParamStatsMaxDecisionCycleEpMemTimeValueSec(), 0);
            long smemMaxTimeCycle = response.GetArgInt(sml.sml_Names.getKParamStatsMaxDecisionCycleSMemTimeCycle(), 0);
            double smemMaxTimeValue = response.GetArgFloat(sml.sml_Names.getKParamStatsMaxDecisionCycleSMemTimeValueSec(), 0);
            
            Scanner epmemTimeScanner = new Scanner(agent.ExecuteCommandLine("epmem -t"));
            double epmemTime = epmemTimeScanner.skip(".+: ").nextDouble();
            
            Scanner epmemStatsScanner = new Scanner(agent.ExecuteCommandLine("epmem -S"));
            long epmemStores = epmemStatsScanner.skip(".+: ").nextLong(); // Time == Stores (more or less)
            epmemStatsScanner.nextLine();
            epmemStatsScanner.nextLine(); // SQLite version
            long epmemBytes = epmemStatsScanner.skip(".+: ").nextLong(); 
            
            Scanner smemTimeScanner = new Scanner(agent.ExecuteCommandLine("smem -t"));
            double smemTime = smemTimeScanner.skip(".+: ").nextDouble(); 
    
            Scanner smemStatsScanner = new Scanner(agent.ExecuteCommandLine("smem -S"));
            smemStatsScanner.nextLine(); // SQLite version
            long smemBytes = smemStatsScanner.skip(".+: ").nextLong();
            smemStatsScanner.nextLine();
            smemStatsScanner.nextLine(); // Memory Highwater
            long smemRetrieves = smemStatsScanner.skip(".+: ").nextLong();
            smemStatsScanner.nextLine();
            long smemQueries = smemStatsScanner.skip(".+: ").nextLong();
            smemStatsScanner.nextLine();
            long smemStores = smemStatsScanner.skip(".+: ").nextLong();
            
            double wallClock = (System.currentTimeMillis() - offset) / 1000.0;
            
            double deltaSmemTime = smemTime - lastSmemTime;
            lastSmemTime = smemTime;
            double smemTotalTimePerDc = deltaDc > 0 ? deltaSmemTime / deltaDc : 0;

            double deltaEpmemTime = epmemTime - lastEpmemTime;
            lastEpmemTime = epmemTime;
            double epmemTotalTimePerDc = deltaDc > 0 ? deltaEpmemTime / deltaDc : 0;
            
            String out = String.format(FORMAT, agent.GetAgentName(), wallClock, dc, ksec, ((ksec * 1000.0) / dc), 
                    tsec, pf, ((ksec * 1000.0) / pf), wmcount, wmmean, wmmax, wmadd, wmrem,
                    maxdctimec, maxdctimev, maxdcwmcc, maxdcwmcv, maxdcpfcc, maxdcpfcv,
                    epmemTime, epmemMaxTimeCycle, epmemMaxTimeValue, epmemBytes, epmemStores, epmemTotalTimePerDc, 
                    smemTime, smemMaxTimeCycle, smemMaxTimeValue, smemBytes, smemRetrieves, smemQueries, smemStores, smemTotalTimePerDc);
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

            agent.ExecuteCommandLine("stats -R"); // reset max stats
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
        
        sb.append(agent.ExecuteCommandLine("learn"));
        sb.append(",");
        
        sb.append("epmem learning ");
        sb.append(agent.ExecuteCommandLine("epmem -g learning"));
        sb.append(",");

        sb.append("smem learning ");
        sb.append(agent.ExecuteCommandLine("smem -g learning"));
        sb.append(",");
        
        sb.append("epmem exclusions: [");
        sb.append(agent.ExecuteCommandLine("epmem -g exclusions"));
        sb.append("]");
        
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
