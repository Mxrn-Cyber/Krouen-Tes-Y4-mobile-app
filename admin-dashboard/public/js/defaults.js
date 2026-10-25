// A starting set of categories (names and icons), added by "Import sample content".
// Edit, hide or delete them on the Categories page.
export const DEFAULT_CATEGORIES = [
  { id: 'beef', nameEn: 'Beef', nameKm: 'សាច់គោ', icon: '🥩' },
  { id: 'chicken', nameEn: 'Chicken', nameKm: 'សាច់មាន់', icon: '🍗' },
  { id: 'pork', nameEn: 'Pork', nameKm: 'សាច់ជ្រូក', icon: '🥓' },
  { id: 'fish', nameEn: 'Fish', nameKm: 'ត្រី', icon: '🐟' },
  { id: 'seafood', nameEn: 'Seafood', nameKm: 'គ្រឿងសមុទ្រ', icon: '🦀' },
  { id: 'soup', nameEn: 'Soup', nameKm: 'សម្ល', icon: '🍲' },
  { id: 'noodles', nameEn: 'Noodles', nameKm: 'មី និងគុយទាវ', icon: '🍜' },
  { id: 'salad', nameEn: 'Salad', nameKm: 'ញាំ និងសាឡាដ', icon: '🥗' },
  { id: 'dessert', nameEn: 'Dessert', nameKm: 'បង្អែម', icon: '🍮' },
  { id: 'cookies', nameEn: 'Cookies', nameKm: 'នំខូគី', icon: '🍪' },
  { id: 'vegetarian', nameEn: 'Vegetarian', nameKm: 'អាហារបួស', icon: '🥦' },
  { id: 'vegan', nameEn: 'Vegan', nameKm: 'អាហារបួសសុទ្ធ', icon: '🌱' },
  { id: 'breakfast', nameEn: 'Breakfast', nameKm: 'អាហារពេលព្រឹក', icon: '🍳' },
  { id: 'pasta', nameEn: 'Pasta', nameKm: 'ប៉ាស្តា', icon: '🍝' },
  { id: 'drinks', nameEn: 'Drinks', nameKm: 'ភេសជ្ជៈ', icon: '☕' },
  { id: 'cocktails', nameEn: 'Cocktails 18+', nameKm: 'ស្រាក្រឡុក 18+', icon: '🍸', adultOnly: true }
].map((c, i) => ({ hidden: false, adultOnly: false, ...c, order: i + 1 }));

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
