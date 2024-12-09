package com.example.ai_meal_planner.model;

import java.util.List;

public class Meal {
    private String name;
    private List<String> ingredients;
    private String recipe;

    public Meal(String name, List<String> ingredients, String recipe) {
        this.name = name;
        this.ingredients = ingredients;
        this.recipe = recipe;
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

	public List<String> getIngredients() {
	    return ingredients;
	}

	public void setIngredients(List<String> ingredients) {
	    this.ingredients = ingredients;
	}

	public String getRecipe() {
	    return recipe;
	}

	public void setRecipe(String recipe) {
	    this.recipe = recipe;
	}

    public boolean containsIngredient(String ingredient) {
        for (String ingr : ingredients) {
            if (ingr.toLowerCase().contains(ingredient.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesRestriction(String restriction) {
        // Simplified logic
        if ("vegetarian".equalsIgnoreCase(restriction) && ingredients.contains("Chicken breast")) {
            return true; // Contains meat
        }
        if ("vegan".equalsIgnoreCase(restriction) && (ingredients.contains("Chicken breast") || ingredients.contains("Tofu"))) {
            return true; // Contains animal products
        }
        // Add more restrictions as needed
        return false;
    }
}
