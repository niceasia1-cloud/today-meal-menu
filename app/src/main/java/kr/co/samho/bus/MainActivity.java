package kr.co.samho.bus;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends android.app.Activity {
    private static final String SOURCE_URL = "http://www.shlu.or.kr/2015/MonthMenu";
    private static final String PREFS = "meal_app";
    private static final int REQ_NOTIFICATIONS = 40;

    private final List<MealDay> days = new ArrayList<>();
    private final List<TextView> dateChips = new ArrayList<>();
    private final List<MealDay> dateChipDays = new ArrayList<>();
    private final List<TextView> mealChips = new ArrayList<>();
    private final List<String> mealOrder = Arrays.asList("breakfast", "lunch", "dinner");

    private LinearLayout content;
    private TextView statusText;
    private String selectedDate;
    private String selectedMeal = "lunch";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadMeals();
        selectedDate = hasDate(today()) ? today() : days.get(0).date;
        buildUi();
        render();
        refreshFromRemote(false);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 248, 250));
        root.setPadding(dp(18), dp(14), dp(18), 0);

        TextView title = new TextView(this);
        title.setText("오늘식단");
        title.setTextColor(Color.rgb(27, 35, 43));
        title.setTextSize(25);
        title.setTypeface(null, 1);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(92, 103, 115));
        statusText.setTextSize(13);
        statusText.setPadding(0, dp(3), 0, dp(12));
        root.addView(statusText);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = actionButton("새로고침");
        refresh.setOnClickListener(v -> refreshFromRemote(true));
        Button remind = actionButton("매일 알림");
        remind.setOnClickListener(v -> enableReminders());
        actions.addView(refresh, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams remindParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        remindParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(remind, remindParams);
        root.addView(actions);

        root.addView(dateScroller());
        root.addView(mealScroller());

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private HorizontalScrollView dateScroller() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(12), 0, dp(8));
        dateChipDays.clear();
        dateChipDays.addAll(daysForDisplay());
        for (MealDay day : dateChipDays) {
            TextView chip = chip(shortDate(day.date));
            chip.setOnClickListener(v -> {
                selectedDate = day.date;
                updateChipStates();
                render();
            });
            dateChips.add(chip);
            row.addView(chip);
        }
        scroll.addView(row);
        return scroll;
    }

    private HorizontalScrollView mealScroller() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, 0, 0, dp(12));
        for (String meal : mealOrder) {
            TextView chip = chip(mealLabel(meal));
            chip.setOnClickListener(v -> {
                selectedMeal = meal;
                updateChipStates();
                render();
            });
            mealChips.add(chip);
            row.addView(chip);
        }
        scroll.addView(row);
        return scroll;
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(28, 113, 141));
        return button;
    }

    private TextView chip(String text) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(14);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setBackgroundResource(R.drawable.chip_bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        params.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(params);
        return chip;
    }

    private void render() {
        content.removeAllViews();
        updateChipStates();
        statusText.setText(statusLine());
        MealDay day = findDay(selectedDate);
        if (day == null) {
            content.addView(messageCard("선택한 날짜의 식단이 아직 없습니다."));
            return;
        }

        Meal meal = day.meals.get(selectedMeal);
        content.addView(todayHeader(day));
        if (meal == null || meal.items.isEmpty()) {
            content.addView(messageCard(mealLabel(selectedMeal) + " 정보가 아직 없습니다."));
        } else {
            content.addView(mealCard(meal));
        }

        content.addView(sectionTitle("이번 주 한눈에"));
        for (MealDay each : daysForDisplay()) {
            content.addView(compactDay(each));
        }
    }

    private View todayHeader(MealDay day) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, dp(4), 0, dp(10));
        TextView date = new TextView(this);
        date.setText(day.date + " " + day.weekday);
        date.setTextSize(18);
        date.setTextColor(Color.rgb(27, 35, 43));
        date.setTypeface(null, 1);
        header.addView(date);
        TextView hint = new TextView(this);
        hint.setText("원본: " + SOURCE_URL);
        hint.setTextSize(12);
        hint.setTextColor(Color.rgb(92, 103, 115));
        hint.setPadding(0, dp(3), 0, 0);
        header.addView(hint);
        return header;
    }

    private View mealCard(Meal meal) {
        LinearLayout card = card();
        TextView label = new TextView(this);
        label.setText(mealLabel(selectedMeal));
        label.setTextSize(13);
        label.setTextColor(Color.rgb(28, 113, 141));
        label.setTypeface(null, 1);
        card.addView(label);
        TextView items = new TextView(this);
        items.setText(join(meal.items));
        items.setTextSize(21);
        items.setTextColor(Color.rgb(27, 35, 43));
        items.setTypeface(null, 1);
        items.setPadding(0, dp(8), 0, dp(8));
        card.addView(items);
        TextView tags = new TextView(this);
        tags.setText(meal.tags.isEmpty() ? "알레르기/주의 태그 없음" : "주의 태그: " + join(meal.tags));
        tags.setTextSize(14);
        tags.setTextColor(Color.rgb(79, 90, 102));
        card.addView(tags);
        return card;
    }

    private View compactDay(MealDay day) {
        LinearLayout card = card();
        TextView title = new TextView(this);
        title.setText(day.date + " " + day.weekday);
        title.setTextSize(15);
        title.setTextColor(Color.rgb(27, 35, 43));
        title.setTypeface(null, 1);
        card.addView(title);
        for (String mealKey : mealOrder) {
            Meal meal = day.meals.get(mealKey);
            TextView line = new TextView(this);
            line.setText(mealLabel(mealKey) + "  " + (meal == null ? "-" : join(meal.items)));
            line.setTextSize(14);
            line.setTextColor(Color.rgb(64, 75, 87));
            line.setPadding(0, dp(5), 0, 0);
            card.addView(line);
        }
        return card;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private TextView messageCard(String message) {
        TextView text = new TextView(this);
        text.setText(message);
        text.setTextSize(15);
        text.setTextColor(Color.rgb(64, 75, 87));
        text.setBackgroundResource(R.drawable.card_bg);
        return text;
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(17);
        title.setTypeface(null, 1);
        title.setTextColor(Color.rgb(27, 35, 43));
        title.setPadding(0, dp(8), 0, dp(8));
        return title;
    }

    private void refreshFromSource() {
        statusText.setText("원본 페이지를 확인하는 중입니다...");
        new Thread(() -> {
            try {
                String html = download(SOURCE_URL);
                List<MealDay> parsed = parseSimpleMenu(html);
                if (parsed.isEmpty()) {
                    saveRawSnapshot(html);
                    runOnUiThread(() -> Toast.makeText(this, "원본은 받았지만 식단 표 구조를 자동 인식하지 못했습니다.", Toast.LENGTH_LONG).show());
                    return;
                }
                saveMeals(parsed, "원본 페이지에서 갱신");
                runOnUiThread(() -> {
                    days.clear();
                    days.addAll(parsed);
                    rebuildAfterDataChange();
                    Toast.makeText(this, "식단을 갱신했습니다.", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "원본 페이지 연결에 실패했습니다. 저장된 식단을 표시합니다.", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void refreshFromRemote(boolean showToast) {
        String remoteUrl = getString(R.string.remote_menu_url);
        if (remoteUrl.contains("YOUR_GITHUB_USERNAME") || remoteUrl.contains("YOUR_REPOSITORY")) {
            if (showToast) {
                Toast.makeText(this, "GitHub Pages JSON URL을 strings.xml에 설정해주세요.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        statusText.setText("최신 식단 데이터를 확인하는 중입니다...");
        new Thread(() -> {
            try {
                String json = download(remoteUrl);
                readMeals(json);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString("meals", json)
                        .putString("updatedAt", now())
                        .putString("source", "GitHub Pages 최신 데이터")
                        .apply();
                runOnUiThread(() -> {
                    selectedDate = hasDate(today()) ? today() : days.get(0).date;
                    rebuildAfterDataChange();
                    if (showToast) {
                        Toast.makeText(this, "최신 식단을 가져왔습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText(statusLine());
                    if (showToast) {
                        Toast.makeText(this, "최신 데이터를 가져오지 못했습니다. 저장된 식단을 표시합니다.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private List<MealDay> parseSimpleMenu(String html) {
        String text = stripHtml(html);
        String[] lines = text.split("\\n+");
        Map<String, MealDay> parsed = new LinkedHashMap<>();
        String currentDate = null;
        String currentMeal = null;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.length() < 2) continue;
            String date = extractDate(line);
            if (date != null) {
                currentDate = date;
                if (!parsed.containsKey(date)) parsed.put(date, new MealDay(date, weekday(date)));
            }
            String meal = mealKeyFromLine(line);
            if (meal != null) currentMeal = meal;
            if (currentDate != null && currentMeal != null && looksLikeMenu(line)) {
                MealDay day = parsed.get(currentDate);
                Meal existing = day.meals.get(currentMeal);
                if (existing == null) {
                    existing = new Meal(new ArrayList<>(), new ArrayList<>());
                    day.meals.put(currentMeal, existing);
                }
                existing.items.addAll(splitMenu(line));
                existing.tags.clear();
                existing.tags.addAll(tagsFor(existing.items));
            }
        }
        return new ArrayList<>(parsed.values());
    }

    private boolean looksLikeMenu(String line) {
        return line.contains("밥") || line.contains("국") || line.contains("김치")
                || line.contains("찌개") || line.contains("볶음") || line.contains("샐러드");
    }

    private List<String> splitMenu(String line) {
        String cleaned = line.replace("조식", "").replace("중식", "").replace("석식", "")
                .replaceAll("\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}", "")
                .replaceAll("\\d{1,2}[-./]\\d{1,2}", "");
        String[] parts = cleaned.split("[,/·|]+|\\s{2,}");
        List<String> items = new ArrayList<>();
        for (String part : parts) {
            String item = part.trim();
            if (item.length() >= 2 && !items.contains(item)) items.add(item);
        }
        return items;
    }

    private void enableReminders() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }
        scheduleReminder("breakfast", 7, 30, 101);
        scheduleReminder("lunch", 11, 20, 102);
        scheduleReminder("dinner", 17, 20, 103);
        Toast.makeText(this, "조식 7:30, 중식 11:20, 석식 17:20 알림을 켰습니다.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableReminders();
        }
    }

    private void scheduleReminder(String meal, int hour, int minute, int requestCode) {
        Intent intent = new Intent(this, MealReminderReceiver.class);
        intent.putExtra("meal", meal);
        PendingIntent pending = PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        if (next.before(Calendar.getInstance())) next.add(Calendar.DATE, 1);
        AlarmManager alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pending);
    }

    private void rebuildAfterDataChange() {
        dateChips.clear();
        dateChipDays.clear();
        mealChips.clear();
        selectedDate = hasDate(today()) ? today() : days.get(0).date;
        buildUi();
        render();
    }

    private void loadMeals() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String stored = prefs.getString("meals", null);
            readMeals(stored == null ? readAsset("meal_current.json") : stored);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load meal data", e);
        }
    }

    private void readMeals(String json) throws Exception {
        days.clear();
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            String date = item.getString("date");
            MealDay day = new MealDay(date, weekday(date));
            JSONObject meals = item.getJSONObject("meals");
            for (String mealKey : mealOrder) {
                if (!meals.has(mealKey)) continue;
                JSONObject mealJson = meals.getJSONObject(mealKey);
                day.meals.put(mealKey, new Meal(toList(mealJson.getJSONArray("items")), toList(mealJson.optJSONArray("tags"))));
            }
            days.add(day);
        }
    }

    private void saveMeals(List<MealDay> parsed, String source) throws Exception {
        JSONArray array = new JSONArray();
        for (MealDay day : parsed) {
            JSONObject dayJson = new JSONObject();
            dayJson.put("date", day.date);
            dayJson.put("weekday", day.weekday);
            JSONObject mealsJson = new JSONObject();
            for (String key : mealOrder) {
                Meal meal = day.meals.get(key);
                if (meal == null) continue;
                JSONObject mealJson = new JSONObject();
                mealJson.put("items", new JSONArray(meal.items));
                mealJson.put("tags", new JSONArray(meal.tags));
                mealsJson.put(key, mealJson);
            }
            dayJson.put("meals", mealsJson);
            array.put(dayJson);
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("meals", array.toString())
                .putString("updatedAt", now())
                .putString("source", source)
                .apply();
    }

    private void saveRawSnapshot(String html) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("rawSnapshot", stripHtml(html))
                .putString("updatedAt", now())
                .putString("source", "원본 수신, 자동 파싱 대기")
                .apply();
    }

    private String download(String value) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(value).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent", "MealToday/0.1 Android");
        InputStream input = connection.getInputStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        input.close();
        return output.toString("UTF-8");
    }

    private String readAsset(String name) throws Exception {
        InputStream input = getAssets().open(name);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        input.close();
        return output.toString("UTF-8");
    }

    private List<String> toList(JSONArray array) throws Exception {
        List<String> values = new ArrayList<>();
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) values.add(array.getString(i));
        return values;
    }

    private String stripHtml(String html) {
        Spanned spanned = Html.fromHtml(html.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n"));
        return spanned.toString().replace('\u00a0', ' ');
    }

    private String extractDate(String line) {
        java.util.regex.Matcher full = java.util.regex.Pattern.compile("(20\\d{2})[-./](\\d{1,2})[-./](\\d{1,2})").matcher(line);
        if (full.find()) return String.format(Locale.KOREA, "%s-%02d-%02d", full.group(1), Integer.parseInt(full.group(2)), Integer.parseInt(full.group(3)));
        java.util.regex.Matcher shortDate = java.util.regex.Pattern.compile("(\\d{1,2})[-./](\\d{1,2})").matcher(line);
        if (shortDate.find()) return String.format(Locale.KOREA, "%s-%02d-%02d", new SimpleDateFormat("yyyy", Locale.KOREA).format(Calendar.getInstance().getTime()), Integer.parseInt(shortDate.group(1)), Integer.parseInt(shortDate.group(2)));
        return null;
    }

    private String mealKeyFromLine(String line) {
        if (line.contains("조식") || line.contains("아침")) return "breakfast";
        if (line.contains("중식") || line.contains("점심")) return "lunch";
        if (line.contains("석식") || line.contains("저녁")) return "dinner";
        return null;
    }

    private List<String> tagsFor(List<String> items) {
        List<String> tags = new ArrayList<>();
        String joined = join(items);
        addTag(tags, joined, "돼지", "돼지고기");
        addTag(tags, joined, "소고기", "소고기");
        addTag(tags, joined, "닭", "닭고기");
        addTag(tags, joined, "계란", "계란");
        addTag(tags, joined, "우유", "유제품");
        addTag(tags, joined, "새우", "해산물");
        addTag(tags, joined, "고등어", "생선");
        return tags;
    }

    private void addTag(List<String> tags, String text, String needle, String tag) {
        if (text.contains(needle) && !tags.contains(tag)) tags.add(tag);
    }

    private String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(value);
        }
        return builder.toString();
    }

    private String mealLabel(String key) {
        if ("breakfast".equals(key)) return "조식";
        if ("dinner".equals(key)) return "석식";
        return "중식";
    }

    private void updateChipStates() {
        for (int i = 0; i < dateChips.size(); i++) {
            TextView chip = dateChips.get(i);
            boolean selected = dateChipDays.get(i).date.equals(selectedDate);
            chip.setSelected(selected);
            chip.setTextColor(selected ? Color.WHITE : Color.rgb(49, 61, 74));
        }
        for (int i = 0; i < mealChips.size(); i++) {
            TextView chip = mealChips.get(i);
            boolean selected = mealOrder.get(i).equals(selectedMeal);
            chip.setSelected(selected);
            chip.setTextColor(selected ? Color.WHITE : Color.rgb(49, 61, 74));
        }
    }

    private String statusLine() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String updated = prefs.getString("updatedAt", "2026년 6월");
        String source = prefs.getString("source", "원본 이미지 OCR 데이터");
        return source + " · " + updated;
    }

    private boolean hasDate(String date) {
        return findDay(date) != null;
    }

    private MealDay findDay(String date) {
        for (MealDay day : days) if (day.date.equals(date)) return day;
        return null;
    }

    private List<MealDay> daysForDisplay() {
        List<MealDay> ordered = new ArrayList<>();
        MealDay selected = findDay(selectedDate);
        if (selected != null) {
            ordered.add(selected);
        }
        for (MealDay day : days) {
            if (selected != null && day.date.equals(selected.date)) {
                continue;
            }
            ordered.add(day);
        }
        return ordered;
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Calendar.getInstance().getTime());
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Calendar.getInstance().getTime());
    }

    private String shortDate(String date) {
        return date.length() >= 10 ? date.substring(5) : date;
    }

    private String weekday(String date) {
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(date));
            return new SimpleDateFormat("E", Locale.KOREA).format(calendar.getTime());
        } catch (Exception e) {
            return "";
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    static class MealDay {
        final String date;
        final String weekday;
        final Map<String, Meal> meals = new LinkedHashMap<>();
        MealDay(String date, String weekday) {
            this.date = date;
            this.weekday = weekday;
        }
    }

    static class Meal {
        final List<String> items;
        final List<String> tags;
        Meal(List<String> items, List<String> tags) {
            this.items = items;
            this.tags = tags;
        }
    }
}
