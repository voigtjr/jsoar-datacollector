soar-datacollector is a simple performance data collection utility for [Soar](http://sitemaker.umich.edu/soar/home) agents. soar-datacollector is a Java library that needs to be included directly in your environment to work.

More general information is in the [slides](https://raw.github.com/voigtjr/soar-datacollector/master/doc/sdc-soar-workshop-31.pdf) from the [31st Soar Workshop](https://web.eecs.umich.edu/~soar/workshop/) (2011).

# Usage

**Create one DataCollector instance** for all agents: 

    DataCollector dc = new DataCollector;

**Optionally configure** (default: 5000 cycles):

*To collect based on decision cycles*:

    dc.setPeriodCycles(10); // onUpdateEvent returns true every 10 decisions

*To collect based on wall time*:

    dc.setPeriodMillis(500); // onUpdateEvent returns true after every 500ms

**Implement callbacks** (or add to existing implementations):

*Start event*:

    // Kernel.RegisterForSystemEvent()
    // smlSystemEventId.smlEVENT_SYSTEM_START
    dc.onStart();

*After all agents pass output*:

    // Kernel.RegisterForUpdateEvent()
    // smlUpdateEventId.smlEVENT_AFTER_ALL_OUTPUT_PHASES
    if (dc.onUpdateEvent()) // once per decision cycle
    {
        // onUpdateEvent => true means call collect
        for (Agent agent : agents)
        {
            dc.collect(agent); // for each agent
        }
    }
    
    // possibly call to prevent catastrophic loss in event of crash
    // dc.flush()
    // but call it in a period greater than one decision cycle

*Stop event*:

    // Kernel.RegisterForSystemEvent()
    // smlSystemEventId.smlEVENT_SYSTEM_STOP
    dc.stop();
    for (Agent agent : agents)
    {
        dc.collect(agent); // each agent
    }

## Example

A [full example](https://github.com/voigtjr/soar-datacollector/blob/master/src/main/java/edu/umich/soar/DataCollectorExample.java) is included in the project source.

# Dependencies

soar-datacollector needs two jars found in [Soar 9.3.1](http://soar.googlecode.com/):

* `sml.jar`
* `soar-smljava-9.3.1.jar`

*The build instructions below refer to the new_build_structure branch of Soar. I will remove this message after it is merged in to the trunk.*

# Building soar-smljava-VERSION.jar

Note that the soar-smljava jar is not built by default, produce it by running:
    scons java_sml_misc

# Eclipse Build

Eclipse project settings are distributed with this project. To build, the SOAR variable inside Eclipse's build path system must be set to the folder where Soar was built or extracted to. It will look in to the java subfolder for the two jar dependencies it needs.

To set the SOAR variable, follow this path through Eclipse:

* soar-datacollector build path
* Libraries tab,
* Add Variable
* Configure Variables
* New Folder
* `SOAR`: `/path/to/soar`

# License

soar-datacollector uses the [BSD](http://www.opensource.org/licenses/bsd-license.php) license, the same as Soar.

Copyright (c) 2012, Jonathan Voigt
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
