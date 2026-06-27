package com.example.expensetracker.data

import com.example.expensetracker.model.Expense
import com.example.expensetracker.model.RecurrenceFrequency
import com.example.expensetracker.model.RecurringExpense
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Generates real [Expense] entries from [RecurringExpense] templates.
 *
 * Called once on app launch. For each active template it:
 *  1. Determines the "generate-from" date: day after [RecurringExpense.lastGeneratedDate],
 *     or [RecurringExpense.startDate] if never generated before.
 *  2. Calculates every due date from that point up to today.
 *  3. Creates an [Expense] for each due date.
 *  4. Returns the new expenses AND updated templates (with [RecurringExpense.lastGeneratedDate]
 *     stamped to today) so they can be persisted.
 */
object RecurringExpenseEngine {

    /**
     * @param recurringExpenses All recurring expense templates from [AppData].
     * @param today             The reference "today" date (defaults to [LocalDate.now]).
     * @return A [Pair] of:
     *   - `first`  → newly created [Expense] entries to add to the expense list
     *   - `second` → updated [RecurringExpense] list with stamped [RecurringExpense.lastGeneratedDate]
     */
    fun generate(
        recurringExpenses: List<RecurringExpense>,
        today: LocalDate = LocalDate.now()
    ): Pair<List<Expense>, List<RecurringExpense>> {

        val newExpenses = mutableListOf<Expense>()
        val updatedTemplates = mutableListOf<RecurringExpense>()

        for (re in recurringExpenses) {
            if (!re.isActive) {
                updatedTemplates.add(re)
                continue
            }

            // Where to start generating from
            val generateFrom: LocalDate = when {
                re.lastGeneratedDate != null -> re.lastGeneratedDate.plusDays(1)
                else -> re.startDate
            }

            val effectiveTo = if (re.endDate != null && re.endDate.isBefore(today)) re.endDate else today

            // Nothing due yet
            if (generateFrom.isAfter(effectiveTo)) {
                updatedTemplates.add(re)
                continue
            }

            val dueDates = dueDates(re, generateFrom, effectiveTo)

            for (date in dueDates) {
                newExpenses.add(
                    Expense(
                        id = UUID.randomUUID().toString(),
                        groupId = UUID.randomUUID().toString(),
                        date = date,
                        storeName = re.storeName,
                        amount = re.amount,
                        category = re.category,
                        subcategory = re.subcategory,
                        itemDescription = re.itemDescription.ifEmpty { re.name },
                        labels = re.labels,
                        paymentMode = re.paymentMode,
                        paidVia = re.paidVia,
                        notes = buildString {
                            append("[Recurring: ${re.name}]")
                            if (re.notes.isNotBlank()) append("\n${re.notes}")
                        }
                    )
                )
            }

            android.util.Log.d(
                "RecurringExpenseEngine",
                "Generated ${dueDates.size} expense(s) for '${re.name}' " +
                    "(from=$generateFrom, to=$effectiveTo, freq=${re.frequency})"
            )

            updatedTemplates.add(
                if (dueDates.isNotEmpty()) re.copy(lastGeneratedDate = today) else re
            )
        }

        return Pair(newExpenses, updatedTemplates)
    }

    // ── Due-date calculators ────────────────────────────────────────────────────

    private fun dueDates(re: RecurringExpense, from: LocalDate, to: LocalDate): List<LocalDate> =
        when (re.frequency) {
            RecurrenceFrequency.DAILY   -> dailyDates(from, to)
            RecurrenceFrequency.WEEKLY  -> weeklyDates(re.dayOfPeriod, from, to)
            RecurrenceFrequency.MONTHLY -> monthlyDates(re.dayOfPeriod, from, to)
            RecurrenceFrequency.YEARLY  -> yearlyDates(re.dayOfPeriod, if (re.monthOfPeriod > 0) re.monthOfPeriod else re.startDate.monthValue, from, to)
        }

    /** Every day from [from] to [to] inclusive. */
    private fun dailyDates(from: LocalDate, to: LocalDate): List<LocalDate> {
        val list = mutableListOf<LocalDate>()
        var cur = from
        while (!cur.isAfter(to)) {
            list.add(cur)
            cur = cur.plusDays(1)
        }
        return list
    }

    /**
     * Every occurrence of [dayOfWeek] (1=Mon … 7=Sun) from [from] to [to] inclusive.
     * Advances [from] forward until it lands on the target day-of-week, then steps by 1 week.
     */
    private fun weeklyDates(dayOfWeek: Int, from: LocalDate, to: LocalDate): List<LocalDate> {
        val target = DayOfWeek.of(dayOfWeek.coerceIn(1, 7))
        // Fast-forward 'from' to the next (or same) occurrence of target DOW
        var cur = from
        while (cur.dayOfWeek != target) cur = cur.plusDays(1)
        val list = mutableListOf<LocalDate>()
        while (!cur.isAfter(to)) {
            list.add(cur)
            cur = cur.plusWeeks(1)
        }
        return list
    }

    /**
     * The [dayOfMonth]th day of every month between [from] and [to].
     * Clamps to the last day of the month for short months (e.g. day 31 in April → April 30).
     */
    private fun monthlyDates(dayOfMonth: Int, from: LocalDate, to: LocalDate): List<LocalDate> {
        val list = mutableListOf<LocalDate>()
        var ym = YearMonth.from(from)
        val endYm = YearMonth.from(to)
        while (!ym.isAfter(endYm)) {
            val day = dayOfMonth.coerceIn(1, ym.lengthOfMonth())
            val date = ym.atDay(day)
            if (!date.isBefore(from) && !date.isAfter(to)) list.add(date)
            ym = ym.plusMonths(1)
        }
        return list
    }

    /**
     * The [dayOfMonth]th day of target month, once per year between [from] and [to].
     */
    private fun yearlyDates(
        dayOfMonth: Int,
        targetMonthValue: Int,
        from: LocalDate,
        to: LocalDate
    ): List<LocalDate> {
        val list = mutableListOf<LocalDate>()
        val targetMonth = java.time.Month.of(targetMonthValue.coerceIn(1, 12))
        for (year in from.year..to.year) {
            val ym = YearMonth.of(year, targetMonth)
            val day = dayOfMonth.coerceIn(1, ym.lengthOfMonth())
            val date = LocalDate.of(year, targetMonth, day)
            if (!date.isBefore(from) && !date.isAfter(to)) list.add(date)
        }
        return list
    }
}
