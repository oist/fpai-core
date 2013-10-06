package org.flexiblepower.rai;

import java.util.Date;

import javax.measure.Measurable;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;

import org.flexiblepower.rai.values.ConstraintList;

/**
 * StorageControlSpace is a ControlSpace to expose energetic flexibility of Storage resource.
 * <p>
 * Storage resources can store electricity and release it when required. They are similar to a buffer but can both take
 * and return energy. Examples: batteries, electrical vehicles.
 * <p>
 * Main parameters are about the storage state and characteristics: total capacity (Wh), State of Charge (%), Charge
 * speed/curve as PowerConstraintList, sefDischarge (W), discharge speed/curve as PowerConstraintList, minimal switch on
 * period, minimal switch off period; additional optional parameters specify the target time at which one wants to
 * achieve a target state of charge (%). There are additional charge/discharge energy turnover losses, therefore there
 * are parameters for charge and discharge efficiency (%).
 * <p>
 * PMSuite - PM Control Specification - v0.6
 */
public final class StorageControlSpace extends BufferControlSpace {

    /**
     * power constraint list to represent discharge speed/curve
     */
    private final ConstraintList<Power> dischargeSpeed;

    /**
     * charge efficiency percentage [0,1] to represent energy turnover loss on charging
     */
    private final float chargeEfficiency;

    /**
     * discharge efficiency percentage [0,1] to represent energy turnover loss on discharging
     */
    private final float dischargeEfficiency;

    /**
     * construct storage control space, which exposes the energetic flexibility of a storage resource.
     * 
     * @param resourceManager
     *            creator of the control space, the manager of the storage resource
     * @param validFrom
     *            is the start time instant of the interval [validFrom,validThru[ for which the control space is valid
     * @param validThru
     *            is the end time instant of the interval [validFrom,validThru[ for which the control space is valid
     * @param expirationTime
     *            time after which the creator will autonomously act when no allocation was received by then. Is
     *            optional, provide null when not specified.
     * @param totalCapacity
     *            total buffer capacity
     * @param stateOfCharge
     *            the current state of charge, percentage expressed as double in [0,1]
     * @param chargeSpeed
     *            is PowerConstraintList to represent charge speed/curve characteristics.
     * @param dischargeSpeed
     *            is PowerConstraintList to represent discharge speed/curve characteristics
     * @param selfDischarge
     *            is self discharge value expressed as Power
     * @param chargeEfficiency
     *            represents energy turnover losses on charging
     * @param dischargeEfficiency
     *            represents energy turnover losses on discharging
     * @param minOnPeriod
     *            minimal switch on period
     * @param minOffPeriod
     *            minimal switch off period
     * @param targetTime
     *            target time at which one wants to achieve the target state of charge. Optional parameter, provide null
     *            when not specified. When specified then targetStateOfCharge should also be specified, when not
     *            specified then targetStateOfCharge should not be specified.
     * @param targetStateOfCharge
     *            target state of charge one wants to achieve at the target time, percentage as double in [0,1].
     *            Optional parameter, provide null when not specified. When specified then targetTime should also be
     *            specified, when not specified then targetTime should not be specified.
     * @throws NullPointerException
     *             when totalCapacity is null
     * @throws NullPointerException
     *             when chargeSpeed is null
     * @throws NullPointerException
     *             when dischargeSpeed is null
     * @throws NullPointerException
     *             when selfDischarge is null
     * @throws NullPointerException
     *             when minOnPeriod is null
     * @throws NullPointerException
     *             when minOffPeriod is null
     * @throws NullPointerException
     *             when targetTime is null and targetStateOfCharge is not null
     * @throws NullPointerException
     *             when targetStateOfCharge is null and targetTime is not null
     * @throws IllegalArgumentException
     *             when stateOfCharge is not null but not in [0,1]
     * @throws IllegalArgumentException
     *             when targetStateOfCharge is not null but not in [0,1]
     * @throws IllegalArgumentException
     *             when chargeEfficiency is not null but not in [0,1]
     * @throws IllegalArgumentException
     *             when dischargeEfficiency is not null but not in [0,1]
     */
    public StorageControlSpace(String resourceId,
                               Date validFrom,
                               Date validThru,
                               Date expirationTime,
                               Measurable<Energy> totalCapacity,
                               float stateOfCharge,
                               ConstraintList<Power> chargeSpeed,
                               ConstraintList<Power> dischargeSpeed,
                               Measurable<Power> selfDischarge,
                               float chargeEfficiency,
                               float dischargeEfficiency,
                               Measurable<Duration> minOnPeriod,
                               Measurable<Duration> minOffPeriod,
                               Date targetTime,
                               Float targetStateOfCharge) {
        super(resourceId,
              validFrom,
              validThru,
              expirationTime,
              totalCapacity,
              stateOfCharge,
              chargeSpeed,
              selfDischarge,
              minOnPeriod,
              minOffPeriod,
              targetTime,
              targetStateOfCharge);
        this.dischargeSpeed = dischargeSpeed;
        this.chargeEfficiency = chargeEfficiency;
        this.dischargeEfficiency = dischargeEfficiency;
        validate();
    }

    private void validate() {
        if (dischargeSpeed == null) {
            throw new NullPointerException("dischargeSpeed is null");
        }
        if (chargeEfficiency < 0 || chargeEfficiency > 1) {
            throw new IllegalArgumentException("chargeEfficiency should be in [0,1] but is " + chargeEfficiency);
        }
        if (dischargeEfficiency < 0 || dischargeEfficiency > 1) {
            throw new IllegalArgumentException("dischargeEfficiency should be in [0,1] but is " + dischargeEfficiency);
        }
        // TODO -- check validity of chargeSpeed contents
        // TODO -- check validity of dischargeSpeed contents
        // TODO -- check relation with expiration time and target time
    }

    /**
     * 
     * @return discharge speed power constraint list
     */
    public ConstraintList<Power> getDischargeSpeed() {
        return dischargeSpeed;
    }

    /**
     * 
     * @return charge efficiency (1 is most efficient, so no energy turnover losses on charging)
     */
    public float getChargeEfficiency() {
        return chargeEfficiency;
    }

    /**
     * 
     * @return discharge efficiency (1 is most efficient, so no energy turnover losses on discharging)
     */
    public float getDischargeEfficiency() {
        return dischargeEfficiency;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Float.floatToIntBits(chargeEfficiency);
        result = prime * result + Float.floatToIntBits(dischargeEfficiency);
        result = prime * result + ((dischargeSpeed == null) ? 0 : dischargeSpeed.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        StorageControlSpace other = (StorageControlSpace) obj;
        if (Float.floatToIntBits(chargeEfficiency) != Float.floatToIntBits(other.chargeEfficiency)) {
            return false;
        }
        if (Float.floatToIntBits(dischargeEfficiency) != Float.floatToIntBits(other.dischargeEfficiency)) {
            return false;
        }
        if (dischargeSpeed == null) {
            if (other.dischargeSpeed != null) {
                return false;
            }
        } else if (!dischargeSpeed.equals(other.dischargeSpeed)) {
            return false;
        }
        return true;
    }

}