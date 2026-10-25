// World dishes the app shows from TheMealDB (food) and TheCocktailDB (drinks).
// Same test key ("1") as the app; both need a paid key before the app goes on Google Play.
const MEAL = 'https://www.themealdb.com/api/json/v1/1/';
const DRINK = 'https://www.thecocktaildb.com/api/json/v1/1/';

/** The app's id for a drink card; food cards use TheMealDB's id as it is. */
export const DRINK_PREFIX = 'drink-';

async function getJson(url) {
  let res;
  try {
    res = await fetch(url);
  } catch (e) {
    throw new Error('Couldn’t reach TheMealDB / TheCocktailDB. Check the internet connection.');
  }
  if (!res.ok) throw new Error(`The recipe service answered ${res.status}. Try again later.`);
  return res.json();
}

const meal = m => ({ id: String(m.idMeal), name: m.strMeal || '', image: m.strMealThumb || '', kind: 'meal' });
const drink = d => ({ id: DRINK_PREFIX + d.idDrink, name: d.strDrink || '', image: d.strDrinkThumb || '', kind: 'drink' });

let categoryCache = null;
/** TheMealDB category names (Beef, Chicken, …). */
export async function mealCategories() {
  if (!categoryCache) {
    const json = await getJson(MEAL + 'categories.php');
    categoryCache = (json.categories || []).map(c => c.strCategory).filter(Boolean);
  }
  return categoryCache;
}

let areaCache = null;
/** TheMealDB country names (as TheMealDB spells them). */
export async function mealAreas() {
  if (!areaCache) {
    const json = await getJson(MEAL + 'list.php?a=list');
    areaCache = (json.meals || []).map(a => a.strArea).filter(Boolean).sort();
  }
  return areaCache;
}

/** Ways to list dishes, as [key, label]. */
export const ONLINE_SOURCES = [
  ['category', 'Food by category'],
  ['area', 'Food by country'],
  ['search', 'Search food by name'],
  ['drinks', 'Drinks (no alcohol)'],
  ['cocktails', 'Cocktails (18+)']
];

/** Dishes for one source: [{id, name, image, kind}]. */
export async function listDishes(source, value = '') {
  const v = encodeURIComponent(value.trim());
  switch (source) {
    case 'category': return ((await getJson(`${MEAL}filter.php?c=${v}`)).meals || []).map(meal);
    case 'area': return ((await getJson(`${MEAL}filter.php?a=${v}`)).meals || []).map(meal);
    case 'search': return v ? ((await getJson(`${MEAL}search.php?s=${v}`)).meals || []).map(meal) : [];
    case 'drinks': return ((await getJson(`${DRINK}filter.php?a=Non_Alcoholic`)).drinks || []).map(drink);
    case 'cocktails': return ((await getJson(`${DRINK}filter.php?c=Cocktail`)).drinks || []).map(drink);
    default: return [];
  }
}
