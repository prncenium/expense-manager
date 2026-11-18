package com.expensemanager.it;

import com.expensemanager.assistant.Insight;
import com.expensemanager.assistant.Insight.Type;
import com.expensemanager.assistant.InsightService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.YearMonth;
import java.util.List;
import java.util.StringJoiner;

import static org.assertj.core.api.Assertions.assertThat;

/** ASSISTANT-SPEC §5: each insight fires at its threshold and not just below it. Target month: Oct 2026. */
class InsightIT extends AbstractIT {

    private static final YearMonth OCT = YearMonth.of(2026, 10);

    @Autowired
    private InsightService insights;

    /** rows: "date,amount,vendor" lines */
    private void load(String... rows) throws Exception {
        StringJoiner csv = new StringJoiner("\n", "date,amount,vendor\n", "\n");
        for (String row : rows) {
            csv.add(row);
        }
        upload(csv.toString()).andReturn();
    }

    private List<Insight> ofType(Type type) {
        return insights.insights(OCT).stream().filter(i -> i.type() == type).toList();
    }

    // ---- SPIKE: up >= 25 % and >= ₹500 ----

    @Test
    void spikeAtTwentyFivePercentAndFiveHundred() throws Exception {
        load("2026-09-07,2000,Uber", "2026-10-05,2500,Uber");
        List<Insight> spikes = ofType(Type.SPIKE);
        assertThat(spikes).hasSize(1);
        Insight s = spikes.get(0);
        assertThat(s.title()).isEqualTo("Transport is up 25% from September 2026");
        assertThat(s.amountText()).isEqualTo("₹2,500");
        assertThat(s.possibleSaving()).isEqualByComparingTo("500");
        assertThat(s.askUser()).contains("Transport");
    }

    @Test
    void noSpikeJustBelowEitherLimit() throws Exception {
        load("2026-09-07,2000,Uber", "2026-10-05,2499,Uber",          // +499
                "2026-09-07,4000,Amazon", "2026-10-05,4999,Amazon");  // +999 but 24.98 %
        assertThat(ofType(Type.SPIKE)).isEmpty();
    }

    @Test
    void noSpikeForACategoryNewThisMonth() throws Exception {
        load("2026-10-05,5000,Amazon");
        assertThat(ofType(Type.SPIKE)).isEmpty();
    }

    // ---- FREQUENT_SMALL: >= 8 times, average <= ₹500 ----

    @Test
    void frequentSmallAtEightOrders() throws Exception {
        load("2026-10-05,200,Swiggy", "2026-10-06,200,swiggy", "2026-10-07,200,Swiggy", "2026-10-08,200,Swiggy",
                "2026-10-12,200,Swiggy", "2026-10-13,200,Swiggy", "2026-10-14,200,Swiggy", "2026-10-15,200,SWIGGY ");
        List<Insight> found = ofType(Type.FREQUENT_SMALL);
        assertThat(found).hasSize(1);
        Insight f = found.get(0);
        assertThat(f.title()).isEqualTo("Swiggy: 8 small spends this month");
        assertThat(f.amount()).isEqualByComparingTo("1600");
        assertThat(f.possibleSavingText()).isEqualTo("₹800");
        assertThat(f.evidence()).contains("average ₹200");
    }

    @Test
    void noFrequentSmallAtSevenOrdersOrAboveFiveHundredAverage() throws Exception {
        load("2026-10-05,200,Swiggy", "2026-10-06,200,Swiggy", "2026-10-07,200,Swiggy", "2026-10-08,200,Swiggy",
                "2026-10-12,200,Swiggy", "2026-10-13,200,Swiggy", "2026-10-14,200,Swiggy",
                "2026-10-05,501,Uber", "2026-10-06,501,Uber", "2026-10-07,501,Uber", "2026-10-08,501,Uber",
                "2026-10-12,501,Uber", "2026-10-13,501,Uber", "2026-10-14,501,Uber", "2026-10-15,501,Uber");
        assertThat(ofType(Type.FREQUENT_SMALL)).isEmpty();
    }

    // ---- SUBSCRIPTION: once a month, within 10 % ----

    @Test
    void subscriptionWhenChargedOnceInEachOfTheLastMonths() throws Exception {
        load("2026-08-03,649,Netflix", "2026-09-08,649,Netflix", "2026-10-08,649,Netflix");
        List<Insight> found = ofType(Type.SUBSCRIPTION);
        assertThat(found).hasSize(1);
        Insight s = found.get(0);
        assertThat(s.title()).isEqualTo("Netflix: about ₹649 every month");
        assertThat(s.evidence()).contains("last 3 months");
        assertThat(s.possibleSaving()).isEqualByComparingTo("649");
        assertThat(s.askUser()).isEqualTo("Do you still use Netflix?");
    }

    @Test
    void noSubscriptionWhenAmountsDifferOrChargedTwiceOrMissingLastMonth() throws Exception {
        load("2026-09-08,649,Netflix", "2026-10-08,720,Netflix",                           // > 10 % apart
                "2026-09-08,199,Spotify", "2026-10-08,199,Spotify", "2026-10-09,199,Spotify", // twice in Oct
                "2026-08-03,299,Hotstar", "2026-10-08,299,Hotstar");                       // no Sept charge
        assertThat(ofType(Type.SUBSCRIPTION)).isEmpty();
    }

    // ---- HIGH_EXPENSE: the existing 3x anomaly rule, at most 3 ----

    @Test
    void highExpensesAreTheBiggestThreeAnomalies() throws Exception {
        load("2026-10-05,100,Swiggy", "2026-10-06,100,Swiggy", "2026-10-07,100,Swiggy", "2026-10-08,1000,Zomato",
                "2026-10-05,100,Uber", "2026-10-06,100,Uber", "2026-10-07,100,Uber", "2026-10-08,1100,Ola",
                "2026-10-05,100,Amazon", "2026-10-06,100,Amazon", "2026-10-07,100,Amazon", "2026-10-08,1200,Flipkart",
                "2026-10-05,100,Shop A", "2026-10-06,100,Shop A", "2026-10-07,100,Shop A", "2026-10-08,1300,Shop B");
        List<Insight> found = ofType(Type.HIGH_EXPENSE);
        assertThat(found).extracting(Insight::title)
                .containsExactly("Shop B ₹1,300 on 8 Oct", "Flipkart ₹1,200 on 8 Oct", "Ola ₹1,100 on 8 Oct");
        assertThat(found.get(2).evidence()).isEqualTo("3.1× your usual Transport expense (average ₹350).");
    }

    // ---- TOP_SHARE: one category over 40 % of a month of >= ₹1,000 ----

    @Test
    void topShareOnlyAboveFortyPercent() throws Exception {
        load("2026-10-05,600,Amazon", "2026-10-06,400,Uber");   // 60 % and exactly 40 %
        List<Insight> found = ofType(Type.TOP_SHARE);
        assertThat(found).extracting(Insight::title).containsExactly("Shopping is 60% of October 2026");
        assertThat(found.get(0).evidence()).isEqualTo("₹600 of ₹1,000 spent this month.");
    }

    @Test
    void noShareInsightsOnASmallMonth() throws Exception {
        load("2026-10-09,600,Amazon", "2026-10-05,300,Uber");   // ₹900 total, mostly on a Friday
        assertThat(ofType(Type.TOP_SHARE)).isEmpty();
        assertThat(ofType(Type.WEEKEND)).isEmpty();
    }

    // ---- WEEKEND: Fri–Sun >= 50 % ----

    @Test
    void weekendAtFiftyPercent() throws Exception {
        load("2026-10-09,500,Swiggy", "2026-10-05,500,Uber");    // Fri 500, Mon 500
        List<Insight> found = ofType(Type.WEEKEND);
        assertThat(found).extracting(Insight::title).containsExactly("50% of October 2026 spend is on Fri–Sun");
        assertThat(found.get(0).amount()).isEqualByComparingTo("500");
    }

    @Test
    void noWeekendJustBelowFiftyPercent() throws Exception {
        load("2026-10-09,499,Swiggy", "2026-10-05,501,Uber");
        assertThat(ofType(Type.WEEKEND)).isEmpty();
    }

    // ---- whole list ----

    @Test
    void sortedByAmountAndOnlyTheChosenMonth() throws Exception {
        load("2026-09-08,649,Netflix", "2026-10-08,649,Netflix",
                "2026-10-05,600,Amazon", "2026-10-06,400,Uber",
                "2026-11-02,9000,Amazon");                          // November: must not count
        List<Insight> all = insights.insights(OCT);
        assertThat(all).isNotEmpty().hasSizeLessThanOrEqualTo(6);
        for (int i = 1; i < all.size(); i++) {
            assertThat(all.get(i - 1).amount()).isGreaterThanOrEqualTo(all.get(i).amount());
        }
        assertThat(all).noneMatch(i -> i.amountText().contains("9,000"));
    }

    @Test
    void emptyMonthGivesNoInsights() {
        assertThat(insights.insights(OCT)).isEmpty();
    }
}
