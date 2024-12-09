package com.example.ai_meal_planner.controller;
import java.nio.charset.StandardCharsets;
import com.example.ai_meal_planner.model.Meal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

@Controller
public class MealPlannerController {

    // Data structures for meal planning
    private Map<String, Meal> meals = new HashMap<>();

    @PostConstruct
    public void init() {
        // Initialize meal data with recipes and ingredients
        initializeMeals();
    }

    @GetMapping("/")
    public String showForm(Model model) {
        return "index";
    }

    @PostMapping("/calculate")
    public String handleCalculation(@RequestParam Map<String, String> params, Model model) {
        // Retrieve parameters
        String goal = params.get("goal");
        String ageStr = params.get("age");
        String weightStr = params.get("weight");
        String heightStr = params.get("height");
        String gender = params.get("gender");
        String activity = params.get("activity");
        String restrictions = params.get("restrictions");
        String allergies = params.get("allergies");

        double age = Double.parseDouble(ageStr);
        double weight = Double.parseDouble(weightStr);
        double height = Double.parseDouble(heightStr);

        // Calculate BMR and TDEE
        double bmr = calculateBMR(gender, weight, height, age);
        double tdee = calculateTDEE(bmr, activity);

        // Generate meal plan
        List<Meal> mealPlan = generateMealPlan(goal, restrictions, allergies);

        // Generate grocery list
        List<String> groceryList = generateGroceryList(mealPlan);

        // Generate meal plan description using OpenAI API
        String mealPlanDescription = getMealPlanDescription(mealPlan, gender, weight, height, age, activity, goal);

        // Set attributes for the Thymeleaf template
        model.addAttribute("bmr", bmr);
        model.addAttribute("tdee", tdee);
        model.addAttribute("mealPlan", mealPlan);
        model.addAttribute("groceryList", groceryList);
        model.addAttribute("mealPlanDescription", mealPlanDescription);

        return "index";
    }

    @PostMapping("/feedback")
    public String handleFeedback(@RequestParam Map<String, String> params, Model model) {
        String feedback = params.get("feedback");
        // Process feedback as needed (e.g., save to database or send an email)
        model.addAttribute("message", "Thank you for your feedback!");
        return "index";
    }

private void initializeMeals() {
    // Read meals from JSON file
    try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("meals.json")) {
        if (inputStream != null) {
            String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(json);
            JSONArray mealArray = jsonObject.getJSONArray("meals");

            for (int i = 0; i < mealArray.length(); i++) {
                JSONObject mealJson = mealArray.getJSONObject(i);
                String name = mealJson.getString("name");
                JSONArray ingredientsJson = mealJson.getJSONArray("ingredients");
                List<String> ingredients = new ArrayList<>();
                for (int j = 0; j < ingredientsJson.length(); j++) {
                    ingredients.add(ingredientsJson.getString(j));
                }
                String recipe = mealJson.getString("recipe");

                meals.put(name, new Meal(name, ingredients, recipe));
            }
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
}


    private double calculateBMR(String gender, double weight, double height, double age) {
        if ("male".equalsIgnoreCase(gender)) {
            return (10 * weight) + (6.25 * height) - (5 * age) + 5;
        } else {
            return (10 * weight) + (6.25 * height) - (5 * age) - 161;
        }
    }

    private double calculateTDEE(double bmr, String activity) {
        double activityFactor = 1.2; // Default sedentary
        switch (activity) {
            case "sedentary":
                activityFactor = 1.2;
                break;
            case "lightly_active":
                activityFactor = 1.375;
                break;
            case "moderately_active":
                activityFactor = 1.55;
                break;
            case "very_active":
                activityFactor = 1.725;
                break;
            case "super_active":
                activityFactor = 1.9;
                break;
            default:
                activityFactor = 1.2;
                break;
        }
        return bmr * activityFactor;
    }

    private List<Meal> generateMealPlan(String goal, String restrictions, String allergies) {
        List<Meal> mealPlan = new ArrayList<>();

        // Convert restrictions and allergies to lists
        List<String> restrictionList = Arrays.asList(restrictions.toLowerCase().split(","));
        List<String> allergyList = Arrays.asList(allergies.toLowerCase().split(","));

        // Filter meals based on restrictions and allergies
        for (Meal meal : meals.values()) {
            if (matchesGoal(meal, goal) && !containsRestrictions(meal, restrictionList) && !containsAllergens(meal, allergyList)) {
                mealPlan.add(meal);
            }
        }
Collections.shuffle(mealPlan);
        // Limit the meal plan to 3 meals for the day
        return mealPlan.subList(0, Math.min(3, mealPlan.size()));
    }

    private boolean matchesGoal(Meal meal, String goal) {
    switch (goal.toLowerCase()) {
        case "Weight Gain":
            // Ensure the meal is high in protein
            return meal.getIngredients().contains("chicken") || meal.getIngredients().contains("fish") || meal.getIngredients().contains("tofu");
        case "Weight Loss":
            // Low-calorie or balanced meals
            return !meal.getIngredients().contains("butter") && !meal.getIngredients().contains("cream");
        case "Maintenance":
            // Regular meals with balanced macros
            return true;
        default:
            return true;
    }
}


    private boolean containsRestrictions(Meal meal, List<String> restrictions) {
        for (String restriction : restrictions) {
            if (!restriction.trim().isEmpty() && meal.matchesRestriction(restriction.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAllergens(Meal meal, List<String> allergies) {
        for (String allergy : allergies) {
            if (!allergy.trim().isEmpty() && meal.containsIngredient(allergy.trim())) {
                return true;
            }
        }
        return false;
    }

    private List<String> generateGroceryList(List<Meal> mealPlan) {
        Set<String> grocerySet = new HashSet<>();
        for (Meal meal : mealPlan) {
            grocerySet.addAll(meal.getIngredients());
        }
        return new ArrayList<>(grocerySet);
    }

    private String getMealPlanDescription(List<Meal> mealPlan, String gender, double weight, double height, double age, String activity, String goal) {
        // Construct the prompt for OpenAI API
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Generate a detailed meal plan for a user with the following attributes:\n");
        promptBuilder.append("Gender: ").append(gender).append("\n");
        promptBuilder.append("Age: ").append(age).append("\n");
        promptBuilder.append("Weight: ").append(weight).append(" kg\n");
        promptBuilder.append("Height: ").append(height).append(" cm\n");
        promptBuilder.append("Activity Level: ").append(activity.replace("_", " ")).append("\n");
        promptBuilder.append("Goal: ").append(goal).append("\n");
        promptBuilder.append("Meal Plan:\n");

        for (Meal meal : mealPlan) {
            promptBuilder.append("- ").append(meal.getName()).append("\n");
        }

        promptBuilder.append("Provide detailed nutritional information and explain why these meals are suitable for the user's goal.");

        String prompt = promptBuilder.toString();

        // Call OpenAI API
        String apiKey = System.getenv("OPENAI_API_KEY"); // Use environment variable
        String response = callOpenAIAPI(prompt, apiKey);

        return response;
    }

    private String callOpenAIAPI(String prompt, String apiKey) {
        try {
            String apiUrl = "https://api.openai.com/v1/chat/completions";

            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true); // For POST
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            JSONObject jsonInput = new JSONObject();
            jsonInput.put("model", "gpt-3.5-turbo");

            JSONArray messages = new JSONArray();
            JSONObject messageContent = new JSONObject();
            messageContent.put("role", "user");
            messageContent.put("content", prompt);
            messages.put(messageContent);

            jsonInput.put("messages", messages);

            OutputStream os = connection.getOutputStream();
            byte[] input = jsonInput.toString().getBytes("utf-8");
            os.write(input, 0, input.length);

            // Read response
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
            StringBuilder responseSB = new StringBuilder();
            String responseLine;

            while ((responseLine = br.readLine()) != null) {
                responseSB.append(responseLine.trim());
            }

            // Parse JSON response
            String responseText = responseSB.toString();
            JSONObject jsonResponse = new JSONObject(responseText);
            JSONArray choices = jsonResponse.getJSONArray("choices");
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject("message");
            String content = message.getString("content");

            return content;

        } catch (Exception e) {
            e.printStackTrace();
            return "Error generating meal plan description.";
        }
    }
}