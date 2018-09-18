package flexiblepower.api.efi.unconstrainedhelper;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Power;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import junit.framework.TestCase;

import org.flexiblepower.api.efi.unconstrainedhelper.Unconstrained;
import org.flexiblepower.efi.unconstrained.RunningModeBehaviour;
import org.flexiblepower.efi.unconstrained.UnconstrainedRegistration;
import org.flexiblepower.efi.unconstrained.UnconstrainedStateUpdate;
import org.flexiblepower.efi.unconstrained.UnconstrainedSystemDescription;
import org.flexiblepower.efi.util.RunningMode;
import org.flexiblepower.efi.util.Timer;
import org.flexiblepower.efi.util.TimerUpdate;
import org.flexiblepower.efi.util.Transition;
import org.flexiblepower.ral.values.CommodityMeasurables;
import org.flexiblepower.ral.values.CommoditySet;

public class UnconstrainedTest extends TestCase {
    private Unconstrained fullUnconstrained;
    private Unconstrained incompleteUnconstrained;
    private UnconstrainedRegistration ur;
    private UnconstrainedStateUpdate usu;
    private UnconstrainedSystemDescription usd;

    @Override
    public void setUp() {
        incompleteUnconstrained = new Unconstrained(UnconstrainedTest.constructTestUnconstrainedRegistration());
        ur = UnconstrainedTest.constructTestElectricalUnconstrainedRegistration();
        fullUnconstrained = new Unconstrained(ur);
        usd = UnconstrainedTest.constructUSD(ur);
        usu = UnconstrainedTest.constructUSU(ur, 45);
    }

    public void testGetReachableRunningModes() {
        assertTrue(incompleteUnconstrained.getReachableRunningModes(new Date()).isEmpty());

        fullUnconstrained.processSystemDescription(usd);
        // Unconstrained device is off (rm 1) and in minimum off time for 5 minutes.
        fullUnconstrained.processStateUpdate(usu);

        Set<Integer> reachableRunningModes = fullUnconstrained.getReachableRunningModeIds(new Date());
        // Minimum Off timer restricts unconstrained from going to rm 2 (on).
        assertTrue(reachableRunningModes.contains(fullUnconstrained.getCurrentRunningModeId()));
        assertEquals(1, fullUnconstrained.getCurrentRunningModeId());
        assertFalse(reachableRunningModes.contains(2));

        // Also in 4 minutes from now, the running mode with id 2 should not be reachable.
        Calendar cal2 = Calendar.getInstance();
        cal2.add(Calendar.MINUTE, 4);
        assertTrue(fullUnconstrained.getReachableRunningModeIds(cal2.getTime()).contains(1));
        assertFalse(fullUnconstrained.getReachableRunningModeIds(cal2.getTime()).contains(2));

        // In 6 minutes from now, the running mode with id 2 should be reachable.
        Calendar cal3 = Calendar.getInstance();
        cal3.add(Calendar.MINUTE, 6);

        assertTrue(fullUnconstrained.getReachableRunningModeIds(cal3.getTime()).contains(2));
        assertTrue(fullUnconstrained.getReachableRunningModeIds(cal3.getTime()).contains(1));
    }

    public void testGetPossibleDemands() {
        fullUnconstrained.processSystemDescription(usd);
        fullUnconstrained.processStateUpdate(usu);
        List<Measurable<Power>> demandList = fullUnconstrained.getPossibleDemands(new Date());
        // Unconstrained device is in must off state.
        assertTrue(demandList.size() == 1);
        assertEquals(0d, demandList.get(0).doubleValue(SI.WATT));
    }

    public void testReceivedMessages() {
        assertFalse(fullUnconstrained.hasReceivedSystemDescription());
        assertFalse(fullUnconstrained.hasReceivedStateUpdate());

        // Ignore state update if system description is not in yet.
        fullUnconstrained.processStateUpdate(usu);
        assertFalse(fullUnconstrained.hasReceivedSystemDescription());
        assertFalse(fullUnconstrained.hasReceivedStateUpdate());

        fullUnconstrained.processSystemDescription(usd);
        assertTrue(fullUnconstrained.hasReceivedSystemDescription());
        fullUnconstrained.processStateUpdate(usu);
        assertTrue(fullUnconstrained.hasReceivedStateUpdate());
    }

    /**
     * Gets the electrical demands of all reachable RunningModes at this moment including the current one. It returns an
     * empty list when there has not been a state update.
     *
     * @param moment
     *            The moment of interest.
     * @return An unordered list of the possible electricity consumption demands of the RunningModes, possibly including
     *         running modes with the same power demand.
     *
     * @throws IllegalArgumentException
     *             When a RunningMode is empty.
     */
    private static UnconstrainedStateUpdate constructUSU(UnconstrainedRegistration ur, double fillLevel) {
        Set<TimerUpdate> timerUpdates = new HashSet<TimerUpdate>();

        Calendar moment = Calendar.getInstance();
        moment.add(Calendar.MINUTE, 5);
        // Set the minimum off timer (timer 2) to be finished 5 minutes from now.
        timerUpdates.add(new TimerUpdate(2, moment.getTime()));

        // Unconstrained device is on and in minimum run mode.
        return new UnconstrainedStateUpdate(ur.getResourceId(),
                                            new Date(),
                                            new Date(),
                                            1,
                                            timerUpdates);
    }

    private static UnconstrainedSystemDescription constructUSD(UnconstrainedRegistration br) {
        // Make an UnconstrainedStateUpdate.
        // This fictional device uses both gas and electricity.
        CommodityMeasurables commodityConsumptionOn = CommodityMeasurables.create()
                                                                          .electricity(Measure.valueOf(1000,
                                                                                                       SI.WATT))
                                                                          .gas(Measure.valueOf(.00025,
                                                                                               NonSI.CUBIC_METRE_PER_SECOND))
                                                                          .build();
        CommodityMeasurables commodityConsumptionOff = CommodityMeasurables.create()
                                                                           .electricity(Measure.valueOf(0,
                                                                                                        SI.WATT))
                                                                           .gas(Measure.valueOf(0,
                                                                                                NonSI.CUBIC_METRE_PER_SECOND))
                                                                           .build();

        RunningModeBehaviour rb_On = new RunningModeBehaviour(commodityConsumptionOn,
                                                              Measure.valueOf(0.24, NonSI.EUR_PER_HOUR));
        RunningModeBehaviour rb_Off = new RunningModeBehaviour(commodityConsumptionOff,
                                                               Measure.valueOf(0.00, NonSI.EUR_PER_HOUR));

        Timer minOnTimer = new Timer(1, "Minimum Run Timer", Measure.valueOf(2, SI.SECOND));
        Set<Timer> onTimerSet = new HashSet<Timer>();
        onTimerSet.add(minOnTimer);

        Timer minOffTimer = new Timer(2, "Minimum Off Timer", Measure.valueOf(2, SI.SECOND));
        Set<Timer> offTimerSet = new HashSet<Timer>();
        offTimerSet.add(minOffTimer);

        Set<Transition> transitionsFromOn = new HashSet<Transition>();
        transitionsFromOn.add(new Transition(2,
                                             onTimerSet,
                                             offTimerSet,
                                             Measure.valueOf(0, NonSI.EUR),
                                             Measure.valueOf(0, SI.SECOND)));

        Set<Transition> transitionsFromOff = new HashSet<Transition>();
        transitionsFromOff.add(new Transition(1,
                                              offTimerSet,
                                              onTimerSet,
                                              Measure.valueOf(0, NonSI.EUR),
                                              Measure.valueOf(0, SI.SECOND)));

        Set<RunningMode<RunningModeBehaviour>> runningModeSet = new HashSet<RunningMode<RunningModeBehaviour>>();
        runningModeSet.add(new RunningMode<RunningModeBehaviour>(2,
                                                                 "rmOn",
                                                                 rb_On,
                                                                 transitionsFromOff));
        runningModeSet.add(new RunningMode<RunningModeBehaviour>(1,
                                                                 "rmOff",
                                                                 rb_Off,
                                                                 transitionsFromOn));

        return new UnconstrainedSystemDescription(br.getResourceId(),
                                                  new Date(),
                                                  new Date(),
                                                  runningModeSet);
    }

    private static UnconstrainedRegistration constructTestElectricalUnconstrainedRegistration() {
        return new UnconstrainedRegistration("BR1",
                                             new Date(),
                                             Measure.zero(SI.SECOND),
                                             CommoditySet.onlyElectricity);
    }

    private static UnconstrainedRegistration constructTestUnconstrainedRegistration() {
        return new UnconstrainedRegistration("BR2",
                                             new Date(),
                                             Measure.zero(SI.SECOND),
                                             CommoditySet.onlyGas);
    }
}
