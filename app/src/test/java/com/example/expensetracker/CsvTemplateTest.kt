package com.example.expensetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards [CSV_TEMPLATE_CONTENT] against column drift.
 *
 * A stray comma in a sample row shifts every later field by one, so the
 * importer silently reads Payment Mode as Paid Via, Split ID as the recurring
 * flag, and so on — the file still imports, just into the wrong columns. These
 * tests pin each sample row to the header's column count and check the fields
 * that shifting would corrupt first.
 */
class CsvTemplateTest {

    private val lines = CSV_TEMPLATE_CONTENT.lines().filter { it.isNotBlank() }
    private val header = parseCsvLine(lines.first())
    private val rows = lines.drop(1).map { parseCsvLine(it) }

    @Test
    fun `header has the expected columns`() {
        assertEquals(16, header.size)
        assertEquals("Date (YYYY-MM-DD)", header[0])
        assertEquals("Quantity", header[7])
        assertEquals("Payment Mode", header[10])
        assertEquals("Paid Via", header[11])
        assertEquals("Split ID", header[12])
        assertEquals("Is Recurring (Yes/No)", header[13])
    }

    @Test
    fun `every sample row has as many fields as the header`() {
        assertTrue("template should ship sample rows", rows.isNotEmpty())
        rows.forEachIndexed { index, fields ->
            assertEquals(
                "row ${index + 1} has ${fields.size} fields but the header has ${header.size}",
                header.size,
                fields.size
            )
        }
    }

    @Test
    fun `quoted label lists stay in one field`() {
        val first = rows[0]
        assertEquals("\"Personal, Urgent\" is a single Labels field", "Personal, Urgent", first[6])
        assertEquals("1", first[7])
        assertEquals("Bag", first[8])
        assertEquals("Weekly milk and eggs", first[9])
    }

    @Test
    fun `payment columns land in the payment fields`() {
        rows.forEach { fields ->
            assertTrue(
                "Payment Mode should be a payment method, was '${fields[10]}'",
                fields[10] in setOf("Credit Card", "Net Banking")
            )
            assertTrue(
                "Paid Via should be a payment app, was '${fields[11]}'",
                fields[11] in setOf("Google Pay", "Other")
            )
        }
    }

    @Test
    fun `split rows share a split id and the recurring row is flagged`() {
        val splitRows = rows.filter { it[12].isNotBlank() }
        assertEquals("two sample rows demonstrate a split", 2, splitRows.size)
        assertEquals("SplitA", splitRows[0][12])
        assertEquals("SplitA", splitRows[1][12])
        splitRows.forEach { assertEquals("split rows are not recurring", "No", it[13]) }

        val recurringRows = rows.filter { it[13].equals("Yes", ignoreCase = true) }
        assertEquals("one sample row demonstrates recurrence", 1, recurringRows.size)
        val recurring = recurringRows.single()
        assertEquals("Weekly", recurring[14])
        assertEquals("2026-12-31", recurring[15])
    }
}
