package factorization.docs;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map.Entry;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class RecipeViewer implements IDocGenerator {

    @Override
    public void process(Typesetter out, String arg) {
        addAll(out, "Workbench Recipes", CraftingManager.getInstance().getRecipeList());
        addAll(out, "Furnace Recipes", FurnaceRecipes.smelting().getSmeltingList().entrySet());
    }
    
    void addAll(Typesetter sb, String label, Iterable list) {
        sb.append("\\title{" + label + "}\n\n");
        for (Object obj : list) {
            addRecipe(sb, obj);
            sb.append("\\nl\n");
        }
        sb.append("\\newpage\n");
    }
    
    void addRecipe(Typesetter sb, Object obj) {
        if (obj instanceof ShapedOreRecipe) {
            addShapedOreRecipe(sb, (ShapedOreRecipe) obj);
        } else if (obj instanceof ShapedRecipes) {
            addShapedRecipes(sb, (ShapedRecipes) obj);
        } else if (obj instanceof ShapelessOreRecipe) {
            addShapelessOreRecipe(sb, (ShapelessOreRecipe) obj);
        } else if (obj instanceof ShapelessRecipes) {
            addShapelessRecipes(sb, (ShapelessRecipes) obj);
        } else {
            try {
                addRecipeWithReflection(sb, obj);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        sb.append("\n\n");
    }
    
    Object genericRecipePrefix(Typesetter sb, IRecipe recipe) {
        ItemStack output = ((IRecipe) recipe).getRecipeOutput();
        if (output == null) return null;
        sb.emitWord(new ItemWord(output, null));
        sb.append(" \\b{" + output.getDisplayName() + "}\n\n");
        return output;
    }
    
    void addShapedOreRecipe(Typesetter sb, ShapedOreRecipe recipe) {
        genericRecipePrefix(sb, recipe);
        int width = ReflectionHelper.getPrivateValue(ShapedOreRecipe.class, recipe, "width");
        int height = ReflectionHelper.getPrivateValue(ShapedOreRecipe.class, recipe, "height");
        Object[] input = recipe.getInput();
        int i = 0;
        for (Object in : input) {
            if (in instanceof ItemStack) {
                ItemStack is = (ItemStack) in;
                sb.append("\\#{" + is.getUnlocalizedName() + "}");
            } else if (in == null) {
                sb.emitWord(new ItemWord(null, null));
            } else {
                convertObject(sb, in);
            }
            i++;
            if (i % width == 0) {
                sb.append("\n\n");
            } else {
                sb.append(" ");
            }
        }
    }
    
    void addShapedRecipes(Typesetter sb, ShapedRecipes recipe) {
        genericRecipePrefix(sb, recipe);
        int width = recipe.recipeWidth;
        for (int i = 0; i < recipe.recipeItems.length; i++) {
            sb.emitWord(new ItemWord(recipe.recipeItems[i], null));
            if (i % width == 2) {
                sb.append("\n\n");
            } else {
                sb.append(" ");
            }
        }
    }
    
    void addShapelessOreRecipe(Typesetter sb, ShapelessOreRecipe recipe) {
        genericRecipePrefix(sb, recipe);
        sb.append("Shapeless: ");
        for (Object obj : recipe.getInput()) {
            convertObject(sb, obj);
        }
    }
    
    void addShapelessRecipes(Typesetter sb, ShapelessRecipes recipe) {
        genericRecipePrefix(sb, recipe);
        sb.append("Shapeless: ");
        for (Object obj : recipe.recipeItems) {
            convertObject(sb, obj);
        }
    }
    
    void addRecipeWithReflection(Typesetter sb, Object recipe) throws IllegalArgumentException, IllegalAccessException {
        if (recipe instanceof ItemStack || recipe instanceof String || recipe.getClass().isArray() || recipe instanceof Collection) {
            convertObject(sb, recipe);
            return;
        }
        Object output = RecipeViewer.class; //Just something that isn't null.
        if (recipe instanceof IRecipe) {
            output = genericRecipePrefix(sb, (IRecipe) recipe);
        } else if (recipe instanceof Entry) {
            Entry ent = (Entry) recipe;
            addRecipeWithReflection(sb, ent.getKey());
            sb.append(" ==> ");
            addRecipeWithReflection(sb, ent.getValue());
            return;
        }
        Field[] fields = recipe.getClass().getDeclaredFields();
        for (Field f : fields) {
            if (!f.isAccessible()) {
                f.setAccessible(true);
            }
            int modifiers = f.getModifiers();
            if ((modifiers & Modifier.STATIC) != 0) continue;
            Object v = f.get(recipe);
            if (v == output) continue;
            if (v instanceof String || v instanceof ItemStack || v instanceof Collection || v.getClass().isArray()) {
                sb.append(f.getName() + ": ");
                convertObject(sb, v);
                sb.append("\\nl ");
            }
        }
    }
    
    void convertObject(Typesetter sb, Object obj) {
        if (obj == null) {
            return;
        }
        if (obj instanceof Item) {
            obj = new ItemStack((Item) obj);
        } else if (obj instanceof Block) {
            obj = new ItemStack((Block) obj);
        }
        
        if (obj instanceof ItemStack) {
            sb.emitWord(new ItemWord((ItemStack) obj, null));
        } else if (obj instanceof String) {
            sb.append(obj.toString());
        } else if (obj.getClass().isArray() && obj.getClass().getComponentType().isInstance(Object.class)) {
            Object[] listy = (Object[]) obj;
            String ret = "";
            for (Object o : listy) {
                convertObject(sb, o);
            }
        } else if (obj instanceof Collection) {
            String ret = "";
            for (Object o : (Collection) obj) {
                convertObject(sb, o);
            }
        } else {
            sb.append(obj.toString());
        }
    }

}
