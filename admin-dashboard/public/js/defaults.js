// The categories the Android app ships with (same names, icons and online sources).
// Used by "Import built-in content" when the database is empty.
export const DEFAULT_CATEGORIES = [
  { id: 'beef', nameEn: 'Beef', nameKm: 'សាច់គោ', icon: '🥩', source: { type: 'mealCategory', value: 'Beef' } },
  { id: 'chicken', nameEn: 'Chicken', nameKm: 'សាច់មាន់', icon: '🍗', source: { type: 'mealCategory', value: 'Chicken' } },
  { id: 'pork', nameEn: 'Pork', nameKm: 'សាច់ជ្រូក', icon: '🥓', source: { type: 'mealCategory', value: 'Pork' } },
  { id: 'fish', nameEn: 'Fish', nameKm: 'ត្រី', icon: '🐟', source: { type: 'mealSearch', value: 'fish' } },
  { id: 'seafood', nameEn: 'Seafood', nameKm: 'គ្រឿងសមុទ្រ', icon: '🦀', source: { type: 'mealCategory', value: 'Seafood' } },
  { id: 'soup', nameEn: 'Soup', nameKm: 'សម្ល', icon: '🍲', source: { type: 'mealSearch', value: 'soup' } },
  { id: 'noodles', nameEn: 'Noodles', nameKm: 'មី និងគុយទាវ', icon: '🍜', source: { type: 'mealSearch', value: 'noodle' } },
  { id: 'salad', nameEn: 'Salad', nameKm: 'ញាំ និងសាឡាដ', icon: '🥗', source: { type: 'mealSearch', value: 'salad' } },
  { id: 'dessert', nameEn: 'Dessert', nameKm: 'បង្អែម', icon: '🍮', source: { type: 'mealCategory', value: 'Dessert' } },
  { id: 'cookies', nameEn: 'Cookies', nameKm: 'នំខូគី', icon: '🍪', source: { type: 'mealSearch', value: 'cookie, biscuit' } },
  { id: 'vegetarian', nameEn: 'Vegetarian', nameKm: 'អាហារបួស', icon: '🥦', source: { type: 'mealCategory', value: 'Vegetarian' } },
  { id: 'vegan', nameEn: 'Vegan', nameKm: 'អាហារបួសសុទ្ធ', icon: '🌱', source: { type: 'mealCategory', value: 'Vegan' } },
  { id: 'breakfast', nameEn: 'Breakfast', nameKm: 'អាហារពេលព្រឹក', icon: '🍳', source: { type: 'mealCategory', value: 'Breakfast' } },
  { id: 'pasta', nameEn: 'Pasta', nameKm: 'ប៉ាស្តា', icon: '🍝', source: { type: 'mealCategory', value: 'Pasta' } },
  { id: 'drinks', nameEn: 'Drinks', nameKm: 'ភេសជ្ជៈ', icon: '☕', source: { type: 'drinks', value: '' } },
  { id: 'cocktails', nameEn: 'Cocktails 18+', nameKm: 'ស្រាក្រឡុក 18+', icon: '🍸', source: { type: 'cocktails', value: '' }, adultOnly: true }
].map((c, i) => ({ hidden: false, adultOnly: false, ...c, order: i + 1 }));

export const SOURCE_TYPES = [
  ['none', 'Only our recipes'],
  ['mealCategory', 'Also TheMealDB category'],
  ['mealSearch', 'Also TheMealDB name search'],
  ['drinks', 'Drinks from TheCocktailDB (no alcohol)'],
  ['cocktails', 'Cocktails from TheCocktailDB (18+)']
];

/** Region for the built-in recipes (only where a source ties the dish to a place). */
export const BUILT_IN_REGIONS = { '-110': 'coast' };

/** Built-in JSON recipe → database recipe. */
export function fromBuiltIn(r) {
  const categoryId = String(r.category?.en || '').toLowerCase();
  return {
    numId: r.id,
    slug: r.slug,
    name: r.name,
    category: r.category,
    categoryId,
    region: BUILT_IN_REGIONS[String(r.id)] || 'everywhere',
    description: r.description || { en: '', km: '' },
    serves: r.serves,
    cookMinutes: r.cookMinutes,
    ingredients: r.ingredients || [],
    steps: r.steps || { en: [], km: [] },
    photo: r.photo || null,
    status: 'published',
    authorName: 'Krouen Tes'
  };
}
