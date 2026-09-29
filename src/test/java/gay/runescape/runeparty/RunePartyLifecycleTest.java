package gay.runescape.runeparty;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class RunePartyLifecycleTest
{
    @Test
    public void recreatesBothExecutorsAfterPluginIsDisabled() throws Exception
    {
        RunePartyPlugin plugin = new RunePartyPlugin();
        ExecutorService stoppedActions = plugin.executor;
        ScheduledExecutorService stoppedTimers = plugin.uiTimerExec;
        stoppedActions.shutdownNow();
        stoppedTimers.shutdownNow();
        try
        {
            plugin.initializeExecutors();
            assertNotSame(stoppedActions, plugin.executor);
            assertNotSame(stoppedTimers, plugin.uiTimerExec);
            assertEquals("loaded", plugin.executor.submit(() -> "loaded").get(5, TimeUnit.SECONDS));
            assertEquals("timer", plugin.uiTimerExec.schedule(() -> "timer", 0, TimeUnit.MILLISECONDS)
                    .get(5, TimeUnit.SECONDS));
        }
        finally
        {
            plugin.executor.shutdownNow();
            plugin.uiTimerExec.shutdownNow();
        }
    }

    @Test
    public void initializationKeepsLiveExecutors()
    {
        RunePartyPlugin plugin = new RunePartyPlugin();
        ExecutorService actions = plugin.executor;
        ScheduledExecutorService timers = plugin.uiTimerExec;
        try
        {
            plugin.initializeExecutors();
            assertSame(actions, plugin.executor);
            assertSame(timers, plugin.uiTimerExec);
        }
        finally
        {
            plugin.executor.shutdownNow();
            plugin.uiTimerExec.shutdownNow();
        }
    }
}
