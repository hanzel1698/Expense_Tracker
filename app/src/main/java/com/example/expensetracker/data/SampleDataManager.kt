package com.example.expensetracker.data

import android.content.Context
import com.example.expensetracker.model.Expense
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

object SampleDataManager {

    // Define complete category structure with proper item mappings
    data class CategoryData(
        val stores: List<String>,
        val subcategories: Map<String, List<String>>, // subcategory -> items
        val amountRange: ClosedFloatingPointRange<Double>
    )

    private val categoryData = mapOf(
        "Food" to CategoryData(
            stores = listOf("Whole Foods", "Trader Joe's", "Target", "Walmart", "Costco", "Starbucks", "Local Cafe", "Restaurant", "Fast Food"),
            subcategories = mapOf(
                "Groceries" to listOf("Organic Vegetables", "Fresh Fruits", "Bread", "Milk", "Cheese", "Yogurt", "Eggs", "Chicken Breast", "Rice", "Pasta"),
                "Coffee" to listOf("Latte", "Cappuccino", "Espresso", "Cold Brew", "Coffee Beans"),
                "Snacks" to listOf("Chips", "Cookies", "Chocolate", "Nuts", "Protein Bar"),
                "Dining Out" to listOf("Dinner", "Lunch", "Appetizers", "Drinks", "Dessert")
            ),
            amountRange = 8.0..120.0
        ),
        "Utilities" to CategoryData(
            stores = listOf("Electric Company", "Internet Provider", "Phone Company", "Gas Company", "Water Company"),
            subcategories = mapOf(
                "Electricity" to listOf("Monthly Bill", "Electric Bill"),
                "Internet" to listOf("WiFi Bill", "Internet Service", "Broadband"),
                "Gas" to listOf("Gas Bill", "Heating Bill")
            ),
            amountRange = 50.0..150.0
        ),
        "Transport" to CategoryData(
            stores = listOf("Shell Gas Station", "BP Gas", "Uber", "Lyft", "Parking Garage", "Metro Station", "Bus Stop"),
            subcategories = mapOf(
                "Fuel" to listOf("Gas Refill", "Fuel", "Premium Gas"),
                "Public Transit" to listOf("Bus Ticket", "Metro Pass", "Train Ticket"),
                "Parking" to listOf("Parking Fee", "Street Parking", "Garage Parking")
            ),
            amountRange = 5.0..60.0
        ),
        "Entertainment" to CategoryData(
            stores = listOf("Netflix", "Spotify", "Movie Theater", "Cinema", "Arcade", "Concert Hall", "Game Store"),
            subcategories = mapOf(
                "Movies" to listOf("Movie Tickets", "Popcorn", "Drinks", "Cinema Snacks"),
                "Music" to listOf("Concert Tickets", "Spotify Premium", "Album Purchase"),
                "Games" to listOf("Video Game", "Board Game", "Arcade Tokens")
            ),
            amountRange = 10.0..80.0
        ),
        "Health" to CategoryData(
            stores = listOf("CVS Pharmacy", "Walgreens", "Gym", "Planet Fitness", "Doctor Office", "Dental Clinic", "Vision Center"),
            subcategories = mapOf(
                "Medicine" to listOf("Prescription", "Vitamins", "Pain Relief", "Cough Syrup", "First Aid"),
                "Doctor" to listOf("Checkup", "Consultation", "Dental Visit", "Eye Exam"),
                "Gym" to listOf("Monthly Membership", "Personal Trainer", "Yoga Class")
            ),
            amountRange = 20.0..200.0
        ),
        "Shopping" to CategoryData(
            stores = listOf("Target", "Amazon", "Best Buy", "Apple Store", "Home Depot", "IKEA", "Macy's", "Nike Store"),
            subcategories = mapOf(
                "Clothing" to listOf("T-Shirt", "Jeans", "Shoes", "Jacket", "Dress"),
                "Electronics" to listOf("Headphones", "Phone Case", "Charger", "Laptop Stand", "Keyboard"),
                "Home" to listOf("Decor", "Furniture", "Bedding", "Kitchen Tools", "Lamp")
            ),
            amountRange = 15.0..400.0
        )
    )

    private val labels = listOf("Personal", "Business", "Urgent", "Recurring", "One-time", "Family", "Gift")
    private val units = listOf("kg", "lbs", "oz", "L", "gal", "pcs", "box", "pack", "bottle", "can")

    fun populateSampleData(): AppData {
        val expenses = mutableListOf<Expense>()
        val today = LocalDate.now()
        val startDate = today.minusMonths(18)

        val categories = categoryData.keys.toList()
        val subcategoriesMap = categoryData.mapValues { it.value.subcategories.keys.toList() }

        var currentDate = startDate

        while (!currentDate.isAfter(today)) {
            val dayOfWeek = currentDate.dayOfWeek.value
            val isWeekend = dayOfWeek >= 6
            val isMonthStart = currentDate.dayOfMonth <= 5
            val isMonthEnd = currentDate.dayOfMonth >= 25

            val dailyExpenseCount = when {
                isWeekend -> Random.nextInt(2, 5)
                isMonthStart -> Random.nextInt(3, 6)
                isMonthEnd -> Random.nextInt(1, 3)
                else -> Random.nextInt(1, 4)
            }

            repeat(dailyExpenseCount) { index ->
                val category = when {
                    isMonthStart && index < 2 -> "Utilities"
                    isWeekend -> listOf("Food", "Entertainment", "Shopping").random()
                    dayOfWeek == 1 -> "Food" // Monday = groceries
                    else -> categories.random()
                }

                val catData = categoryData[category]!!
                val store = catData.stores.random()
                val subcategory = catData.subcategories.keys.random()
                val items = catData.subcategories[subcategory]!!

                val isSplit = Random.nextDouble() < 0.15 && category == "Food"

                if (isSplit) {
                    val groupId = UUID.randomUUID().toString()
                    val selectedItems = items.shuffled().take(Random.nextInt(2, 4))
                    val baseAmount = Random.nextDouble(catData.amountRange.start, catData.amountRange.endInclusive)
                    val itemAmounts = selectedItems.map { Random.nextDouble(5.0, baseAmount / selectedItems.size + 15) }
                    val totalItemAmount = itemAmounts.sum()
                    val scaleFactor = baseAmount / totalItemAmount

                    selectedItems.forEachIndexed { itemIndex, itemName ->
                        expenses.add(
                            Expense(
                                id = UUID.randomUUID().toString(),
                                groupId = groupId,
                                date = currentDate,
                                storeName = store,
                                amount = kotlin.math.round((itemAmounts[itemIndex] * scaleFactor) * 100) / 100,
                                category = category,
                                subcategory = subcategory,
                                itemDescription = itemName,
                                labels = labels.shuffled().take(Random.nextInt(1, 3)),
                                quantity = if (Random.nextBoolean()) Random.nextDouble(1.0, 5.0).let { kotlin.math.round(it * 10) / 10 } else null,
                                unit = if (Random.nextBoolean()) units.random() else null
                            )
                        )
                    }
                } else {
                    val item = items.random()
                    val amount = when {
                        isWeekend && category == "Shopping" -> Random.nextDouble(catData.amountRange.start, catData.amountRange.endInclusive) * 1.5
                        else -> Random.nextDouble(catData.amountRange.start, catData.amountRange.endInclusive)
                    }

                    expenses.add(
                        Expense(
                            id = UUID.randomUUID().toString(),
                            groupId = UUID.randomUUID().toString(),
                            date = currentDate,
                            storeName = store,
                            amount = kotlin.math.round(amount * 100) / 100,
                            category = category,
                            subcategory = subcategory,
                            itemDescription = item,
                            labels = labels.shuffled().take(Random.nextInt(1, 3)),
                            quantity = if (Random.nextBoolean() && category == "Food") Random.nextDouble(1.0, 10.0).let { kotlin.math.round(it * 10) / 10 } else null,
                            unit = if (Random.nextBoolean() && category == "Food") units.random() else null
                        )
                    )
                }
            }

            currentDate = currentDate.plusDays(1)
        }

        // Create budgets based on category spending patterns
        val categoryBudgets = mapOf(
            "Food" to 600.0,
            "Utilities" to 300.0,
            "Transport" to 250.0,
            "Entertainment" to 200.0,
            "Health" to 300.0,
            "Shopping" to 400.0
        )

        return AppData(
            expenses = expenses.sortedByDescending { it.date },
            categories = categories,
            subcategoriesMap = subcategoriesMap,
            labels = labels,
            categoryBudgets = categoryBudgets,
            isDarkTheme = false
        )
    }

    fun clearAllData(context: Context): AppData {
        return AppData()
    }
}
