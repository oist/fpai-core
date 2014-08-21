package org.flexiblepower.rai.values;

import java.util.ArrayList;
import java.util.List;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;
import javax.measure.quantity.Quantity;
import javax.measure.quantity.Volume;
import javax.measure.quantity.VolumetricFlowRate;
import javax.measure.unit.Unit;

import org.flexiblepower.rai.values.CommodityProfile.CommodityProfileElement;

public class CommodityProfile<BQ extends Quantity, FQ extends Quantity> extends
                                                                        Profile<CommodityProfileElement<BQ, FQ>> {
    public static class Map extends Commodity.Map<CommodityProfile<?, ?>> {
        public Map(CommodityProfile<Energy, Power> electricityProfile,
                   CommodityProfile<Volume, VolumetricFlowRate> gasProfile) {
            super(electricityProfile, gasProfile);
        }

        public <BQ extends Quantity, FQ extends Quantity> CommodityProfile<BQ, FQ> get(Commodity<BQ, FQ> commodity) {
            return get(commodity);
        }
    }

    public static class Builder<BQ extends Quantity, FQ extends Quantity> {
        private final Commodity<BQ, FQ> commodity;

        private final List<CommodityProfileElement<BQ, FQ>> elements;
        private Measurable<Duration> duration;
        private Unit<BQ> unit;

        public Builder(Commodity<BQ, FQ> commodity) {
            this.commodity = commodity;
            this.elements = new ArrayList<CommodityProfileElement<BQ, FQ>>();
        }

        public Builder<BQ, FQ> set(Measurable<Duration> duration) {
            this.duration = duration;
            return this;
        }

        public Builder<BQ, FQ> setUnit(Unit<BQ> unit) {
            this.unit = unit;
            return this;
        }

        public Builder<BQ, FQ> add(Measurable<Duration> duration, Measurable<BQ> amount) {
            elements.add(new CommodityProfileElement<BQ, FQ>(commodity, duration, amount));
            return this;
        }

        public Builder<BQ, FQ> add(Measurable<BQ> amount) {
            if (duration == null) {
                throw new IllegalStateException("duration not set");
            }
            elements.add(new CommodityProfileElement<BQ, FQ>(commodity, duration, amount));
            return this;
        }

        public Builder<BQ, FQ> add(double amount) {
            if (duration == null) {
                throw new IllegalStateException("duration not set");
            } else if (unit == null) {
                throw new IllegalStateException("unit not set");
            }
            elements.add(new CommodityProfileElement<BQ, FQ>(commodity, duration, Measure.valueOf(amount, unit)));
            return this;
        }

        @SuppressWarnings("unchecked")
        public CommodityProfile<BQ, FQ> build() {
            return new CommodityProfile<BQ, FQ>(elements.toArray(new CommodityProfileElement[0]));
        }
    }

    public static <BQ extends Quantity, FQ extends Quantity> Builder<BQ, FQ> create(Commodity<BQ, FQ> commodity) {
        return new Builder<BQ, FQ>(commodity);
    }

    public static class CommodityProfileElement<BQ extends Quantity, FQ extends Quantity> implements
                                                                                          ProfileElement<CommodityProfileElement<BQ, FQ>> {

        private final Commodity<BQ, FQ> commodity;
        private final Measurable<Duration> duration;
        private final Measurable<BQ> amount;

        public CommodityProfileElement(Commodity<BQ, FQ> commodity, Measurable<Duration> duration, Measurable<BQ> amount) {
            super();
            this.commodity = commodity;
            this.duration = duration;
            this.amount = amount;
        }

        @Override
        public Measurable<Duration> getDuration() {
            return duration;
        }

        @Override
        public CommodityProfileElement<BQ, FQ> subProfile(Measurable<Duration> offset, Measurable<Duration> duration) {
            final double oldDurationDouble = this.duration.doubleValue(Duration.UNIT);
            final double newDurationDouble = duration.doubleValue(Duration.UNIT);
            final double amountDouble = amount.doubleValue(commodity.getBillableUnit());
            final double newAmountDouble = amountDouble / oldDurationDouble * newDurationDouble;
            final Measure<Double, BQ> newAmount = Measure.valueOf(newAmountDouble, commodity.getBillableUnit());
            return new CommodityProfileElement<BQ, FQ>(commodity, duration, newAmount);
        }

        public Measurable<BQ> getAmount() {
            return amount;
        }

        public Measurable<FQ> getAverage() {
            return commodity.average(amount, duration);
        }

        public Commodity<BQ, FQ> getCommodity() {
            return commodity;
        }

    }

    public CommodityProfile(CommodityProfileElement<BQ, FQ>[] elements) {
        super(elements);
        validate();
    }

    private void validate() {
        // Check if profile is empty
        if (elements.length == 0) {
            throw new IllegalArgumentException("A CommodityProfile cannot be empty");
        }
        // Check if all the commodities are the same
        final Commodity<BQ, FQ> commodity = elements[0].getCommodity();
        for (int i = 1; i < elements.length; i++) {
            if (elements[i].getCommodity() != commodity) {
                throw new IllegalArgumentException("A CommodityProfile can only consist of commodites of the same type");
            }
        }
    }

    public Commodity<BQ, FQ> getCommodity() {
        // Validate makes sure there is at least one element
        return elements[0].getCommodity();
    }

    public Measurable<BQ> getTotalAmount() {
        double amount = 0;
        final Unit<BQ> billableUnit = getCommodity().getBillableUnit();
        for (final CommodityProfileElement<BQ, FQ> e : elements) {
            amount += e.getAmount().doubleValue(billableUnit);
        }
        return Measure.valueOf(amount, billableUnit);
    }

    public Measurable<FQ> getAverage() {
        return getCommodity().average(getTotalAmount(), getTotalDuration());
    }

}
