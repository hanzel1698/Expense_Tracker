// ── Sample data ───────────────────────────────────────────────────────────────
// Port of data/SampleDataManager.kt — ~18 months of plausible expenses used by
// the hidden developer mode in Settings.

import { createExpense } from './model.js';
import { uuid, today, minusMonths, plusDays, dayOfWeek, dayOfMonth, isAfter } from './util.js';

const categoryData = {
  Food: {
    stores: ['Whole Foods', "Trader Joe's", 'Target', 'Walmart', 'Costco', 'Starbucks', 'Local Cafe', 'Restaurant', 'Fast Food'],
    subcategories: {
      Groceries: ['Organic Vegetables', 'Fresh Fruits', 'Bread', 'Milk', 'Cheese', 'Yogurt', 'Eggs', 'Chicken Breast', 'Rice', 'Pasta'],
      Coffee: ['Latte', 'Cappuccino', 'Espresso', 'Cold Brew', 'Coffee Beans'],
      Snacks: ['Chips', 'Cookies', 'Chocolate', 'Nuts', 'Protein Bar'],
      'Dining Out': ['Dinner', 'Lunch', 'Appetizers', 'Drinks', 'Dessert'],
    },
    amountRange: [8, 120],
  },
  Utilities: {
    stores: ['Electric Company', 'Internet Provider', 'Phone Company', 'Gas Company', 'Water Company'],
    subcategories: {
      Electricity: ['Monthly Bill', 'Electric Bill'],
      Internet: ['WiFi Bill', 'Internet Service', 'Broadband'],
      Gas: ['Gas Bill', 'Heating Bill'],
    },
    amountRange: [50, 150],
  },
  Transport: {
    stores: ['Shell Gas Station', 'BP Gas', 'Uber', 'Lyft', 'Parking Garage', 'Metro Station', 'Bus Stop'],
    subcategories: {
      Fuel: ['Gas Refill', 'Fuel', 'Premium Gas'],
      'Public Transit': ['Bus Ticket', 'Metro Pass', 'Train Ticket'],
      Parking: ['Parking Fee', 'Street Parking', 'Garage Parking'],
    },
    amountRange: [5, 60],
  },
  Entertainment: {
    stores: ['Netflix', 'Spotify', 'Movie Theater', 'Cinema', 'Arcade', 'Concert Hall', 'Game Store'],
    subcategories: {
      Movies: ['Movie Tickets', 'Popcorn', 'Drinks', 'Cinema Snacks'],
      Music: ['Concert Tickets', 'Spotify Premium', 'Album Purchase'],
      Games: ['Video Game', 'Board Game', 'Arcade Tokens'],
    },
    amountRange: [10, 80],
  },
  Health: {
    stores: ['CVS Pharmacy', 'Walgreens', 'Gym', 'Planet Fitness', 'Doctor Office', 'Dental Clinic', 'Vision Center'],
    subcategories: {
      Medicine: ['Prescription', 'Vitamins', 'Pain Relief', 'Cough Syrup', 'First Aid'],
      Doctor: ['Checkup', 'Consultation', 'Dental Visit', 'Eye Exam'],
      Gym: ['Monthly Membership', 'Personal Trainer', 'Yoga Class'],
    },
    amountRange: [20, 200],
  },
  Shopping: {
    stores: ['Target', 'Amazon', 'Best Buy', 'Apple Store', 'Home Depot', 'IKEA', "Macy's", 'Nike Store'],
    subcategories: {
      Clothing: ['T-Shirt', 'Jeans', 'Shoes', 'Jacket', 'Dress'],
      Electronics: ['Headphones', 'Phone Case', 'Charger', 'Laptop Stand', 'Keyboard'],
      Home: ['Decor', 'Furniture', 'Bedding', 'Kitchen Tools', 'Lamp'],
    },
    amountRange: [15, 400],
  },
};

const SAMPLE_LABELS = ['Personal', 'Business', 'Urgent', 'Recurring', 'One-time', 'Family', 'Gift'];
const UNITS = ['kg', 'lbs', 'oz', 'L', 'gal', 'pcs', 'box', 'pack', 'bottle', 'can'];

const randInt = (from, until) => from + Math.floor(Math.random() * (until - from));
const randDouble = (from, until) => from + Math.random() * (until - from);
const pick = (list) => list[Math.floor(Math.random() * list.length)];
const shuffled = (list) => [...list].sort(() => Math.random() - 0.5);

export function populateSampleData() {
  const expenses = [];
  const todayIso = today();
  const startDate = minusMonths(todayIso, 18);

  const categories = Object.keys(categoryData);
  const subcategoriesMap = Object.fromEntries(
    Object.entries(categoryData).map(([cat, d]) => [cat, Object.keys(d.subcategories)]),
  );

  let currentDate = startDate;
  while (!isAfter(currentDate, todayIso)) {
    const dow = dayOfWeek(currentDate);
    const dom = dayOfMonth(currentDate);
    const isWeekend = dow >= 6;
    const isMonthStart = dom <= 5;
    const isMonthEnd = dom >= 25;

    const dailyExpenseCount = isWeekend ? randInt(2, 5)
      : isMonthStart ? randInt(3, 6)
        : isMonthEnd ? randInt(1, 3)
          : randInt(1, 4);

    for (let index = 0; index < dailyExpenseCount; index++) {
      const category = (isMonthStart && index < 2) ? 'Utilities'
        : isWeekend ? pick(['Food', 'Entertainment', 'Shopping'])
          : dow === 1 ? 'Food'
            : pick(categories);

      const catData = categoryData[category];
      const store = pick(catData.stores);
      const subcategory = pick(Object.keys(catData.subcategories));
      const items = catData.subcategories[subcategory];
      const [lo, hi] = catData.amountRange;

      const isSplit = Math.random() < 0.15 && category === 'Food';

      if (isSplit) {
        const groupId = uuid();
        const selectedItems = shuffled(items).slice(0, randInt(2, 4));
        const baseAmount = randDouble(lo, hi);
        const itemAmounts = selectedItems.map(() => randDouble(5, baseAmount / selectedItems.length + 15));
        const totalItemAmount = itemAmounts.reduce((a, b) => a + b, 0);
        const scale = baseAmount / totalItemAmount;

        selectedItems.forEach((itemName, itemIndex) => {
          expenses.push(createExpense({
            groupId,
            date: currentDate,
            storeName: store,
            amount: Math.round(itemAmounts[itemIndex] * scale * 100) / 100,
            category,
            subcategory,
            itemDescription: itemName,
            labels: shuffled(SAMPLE_LABELS).slice(0, randInt(1, 3)),
            quantity: Math.random() < 0.5 ? Math.round(randDouble(1, 5) * 10) / 10 : null,
            unit: Math.random() < 0.5 ? pick(UNITS) : null,
          }));
        });
      } else {
        const item = pick(items);
        const amount = (isWeekend && category === 'Shopping')
          ? randDouble(lo, hi) * 1.5
          : randDouble(lo, hi);

        expenses.push(createExpense({
          date: currentDate,
          storeName: store,
          amount: Math.round(amount * 100) / 100,
          category,
          subcategory,
          itemDescription: item,
          labels: shuffled(SAMPLE_LABELS).slice(0, randInt(1, 3)),
          quantity: Math.random() < 0.5 && category === 'Food' ? Math.round(randDouble(1, 10) * 10) / 10 : null,
          unit: Math.random() < 0.5 && category === 'Food' ? pick(UNITS) : null,
        }));
      }
    }

    currentDate = plusDays(currentDate, 1);
  }

  return {
    expenses: expenses.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0)),
    categories,
    subcategoriesMap,
    labels: SAMPLE_LABELS,
    paymentModes: ['Cash', 'Credit Card', 'Debit Card', 'UPI', 'Net Banking', 'Wallet'],
    paidVia: ['Google Pay', 'PhonePe', 'Paytm', 'Amazon Pay', 'BHIM', 'Other'],
    categoryBudgets: {
      Food: 600, Utilities: 300, Transport: 250,
      Entertainment: 200, Health: 300, Shopping: 400,
    },
    subcategoryBudgets: {},
    storeHistory: [],
    storeLocationHistory: {},
    isDarkTheme: false,
    recurringExpenses: [],
  };
}
