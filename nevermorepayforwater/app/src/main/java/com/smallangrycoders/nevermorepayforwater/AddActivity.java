package com.smallangrycoders.nevermorepayforwater;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDateTime;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AddActivity extends Activity {
    private Button btFindCoor, btSave, btCancel;
    private EditText etLoc, etLat, etLon;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_activity);
        btSave = (Button) findViewById(R.id.butSave);
        btCancel = (Button) findViewById(R.id.butCancel);
        btFindCoor = (Button) findViewById(R.id.butFindCoor);
        etLoc = (EditText) findViewById(R.id.City);
        etLat = (EditText) findViewById(R.id.etLat);
        etLon = (EditText) findViewById(R.id.etLon);

        btSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String city = etLoc.getText().toString().trim();
                String latStr = etLat.getText().toString().trim();
                String lonStr = etLon.getText().toString().trim();

                if (city.isEmpty() || latStr.isEmpty() || lonStr.isEmpty()) {
                    showToast("Пожалуйста, заполните все поля");
                    return;
                }

                double lat, lon;
                try {
                    lat = Double.parseDouble(latStr);
                    lon = Double.parseDouble(lonStr);
                } catch (NumberFormatException e) {
                    showToast("Широта и долгота должны быть числами");
                    return;
                }

                if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    showToast("Недопустимые координаты");
                    return;
                }

                StCity stcity = new StCity(-1, city, "0", latStr, lonStr, 1, LocalDateTime.now());
                Intent intent = getIntent();
                intent.putExtra("StCity", stcity);
                setResult(RESULT_OK, intent);
                finish();
            }
        });

        btCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btFindCoor.setOnClickListener(view -> findAndDisplayCoordinates());


    }


    /**
     * @brief Обрабатывает нажатие кнопки поиска координат
     *
     * Проверяет введенное название города и запускает процесс геокодирования.
     *
     * @post Если город не введен - показывает Toast-уведомление
     * @post Вызывает makeGeocodingRequest() для выполнения запроса
     *
     */
    private void findAndDisplayCoordinates() {
        String cityName = etLoc.getText().toString().trim();
        if (cityName.isEmpty()) {
            showToast("Введите название города");
            return;
        }

        makeGeocodingRequest(cityName);
    }

    /**
     * @brief Выполняет HTTP-запрос к Nominatim API
     *
     * Создает и отправляет асинхронный запрос к сервису геокодирования.
     * Обрабатывает базовые ошибки сети и сервера.
     *
     * @param cityName Название города для поиска (URL-кодируется внутри)
     *
     * @note Требует User-Agent header согласно политике Nominatim
     * @warning Не вызывать из UI-потока (использует сетевые операции)
     *
     * @onSuccess Вызывает processGeocodingResponse() с полученными данными
     * @onFailure Вызывает handleGeocodingResult() с сообщением об ошибке
     *
     */
    private void makeGeocodingRequest(String cityName) {
        OkHttpClient client = new OkHttpClient();
        String url = "https://nominatim.openstreetmap.org/search?q=" +
                URLEncoder.encode(cityName) +
                "&format=json&limit=1";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "nevermorepayforwater")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                handleGeocodingResult(null, "Ошибка соединения");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    handleGeocodingResult(null, "Ошибка сервера");
                    return;
                }
                processGeocodingResponse(response.body().string());
            }
        });
    }

    /**
     * @brief Обрабатывает JSON-ответ от сервиса геокодирования
     *
     * Парсит ответ сервера, извлекает координаты и название локации.
     * Обрабатывает ошибки парсинга JSON и пустые результаты.
     *
     * @param jsonData Сырой JSON-ответ от сервера
     *
     * @throws JSONException При ошибках парсинга (перехватывается внутри)
     *
     * @onSuccess Вызывает handleGeocodingResult() с данными локации
     * @onEmptyResult Вызывает handleGeocodingResult() с сообщением "Город не найден"
     * @onParseError Вызывает handleGeocodingResult() с сообщением об ошибке данных
     *
     * @post Всегда закрывает ProgressDialog через handleGeocodingResult()
     *
     */
    private void processGeocodingResponse(String jsonData) {
        try {
            JSONArray jsonArray = new JSONArray(jsonData);
            if (jsonArray.length() > 0) {
                JSONObject firstResult = jsonArray.getJSONObject(0);
                handleGeocodingResult(firstResult, null);
            } else {
                handleGeocodingResult(null, "Город не найден");
            }
        } catch (JSONException e) {
            handleGeocodingResult(null, "Ошибка обработки данных");
        }
    }


    /**
     * @brief Универсальный обработчик результатов геокодирования
     *
     * Обновляет UI на основе результатов запроса или ошибки.
     * Всегда выполняется в UI-потоке.
     *
     * @param result JSONObject с данными локации (может быть null)
     * @param error Сообщение об ошибке (null если результат успешный)
     *
     * @pre Вызывается только из UI-потока (через runOnUiThread)
     *
     * @onError Показывает Toast с сообщением об ошибке
     * @onSuccess Обновляет поля ввода (название, широта, долгота)
     *
     * @post Всегда закрывает ProgressDialog
     * @post Гарантирует обновление UI в главном потоке
     */
    private void handleGeocodingResult(JSONObject result, String error) {
        runOnUiThread(() -> {

            if (error != null) {
                Toast.makeText(AddActivity.this, error, Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                etLat.setText(result.getString("lat"));
                etLon.setText(result.getString("lon"));
            } catch (JSONException e) {
                Toast.makeText(AddActivity.this, "Ошибка формата данных", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
