package com.smallangrycoders.nevermorepayforwater;

import android.app.Activity;
import android.content.Context;

import android.content.Intent;
import android.os.Bundle;

import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import android.widget.ProgressBar;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    DBCities stcConnector;
    Context oContext;
    ArrayList<StCity> states = new ArrayList<>();
    StCityAdapter adapter;
    int ADD_ACTIVITY = 0;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Важно: findViewById только после setContentView
        progressBar = findViewById(R.id.progressBar);

        RecyclerView recyclerView = findViewById(R.id.list);
        oContext = this;
        stcConnector = new DBCities(this);
        adapter = new StCityAdapter(this, stcConnector.selectAll(), null, oContext);

        StCityAdapter.OnStCityClickListener stateClickListener = (state, position) -> {
            // Показать прогресс перед запросом
            runOnUiThread(() -> progressBar.setVisibility(ProgressBar.VISIBLE));

            sendPOST(state, adapter);
            state.setSyncDate(LocalDateTime.now());
        };

        adapter.SetOnCl(stateClickListener);
        recyclerView.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private void updateList() {
        adapter.setArrayMyData(stcConnector.selectAll());
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.add:
                Intent i = new Intent(oContext, AddActivity.class);
                startActivityForResult(i, ADD_ACTIVITY);
                return true;
            case R.id.deleteAll:
                stcConnector.deleteAll();
                updateList();
                return true;
            case R.id.exit:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            StCity st = (StCity) data.getExtras().getSerializable("StCity");
            stcConnector.insert(st.getName(), st.getTemp(), st.getStrLat(), st.getStrLon(), st.getFlagResource(), st.getSyncDate());
            updateList();
        }
    }

    public void sendPOST(StCity state, StCityAdapter adapter) {
        OkHttpClient client = new OkHttpClient();
        String foreAddr = oContext.getString(R.string.forecast_addr);
        HttpUrl.Builder urlBuilder = HttpUrl.parse(foreAddr
                        + oContext.getString(R.string.lat_condition) + state.getStrLat()
                        + oContext.getString(R.string.lon_condition) + state.getStrLon()
                        + oContext.getString(R.string.add_condition))
                .newBuilder();
        String url = urlBuilder.build().toString();
        Request request = new Request.Builder()
                .url(url)
                .cacheControl(new CacheControl.Builder().maxStale(3, TimeUnit.SECONDS).build())
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                if (!response.isSuccessful()) {
                    MainActivity.this.runOnUiThread(() -> {
                        state.setTemp("Ошибка сети");
                        adapter.notifyDataSetChanged();
                        stcConnector.update(state);
                        Toast.makeText(oContext, "Ошибка соединения", Toast.LENGTH_SHORT).show();

                        // Скрываем прогресс
                        progressBar.setVisibility(ProgressBar.GONE);
                    });
                } else {
                    final String responseData = response.body().string();
                    try {
                        JSONObject jo = new JSONObject(responseData);
                        String tempFromAPI = jo
                                .getJSONObject(oContext.getString(R.string.cur_weather))
                                .get(oContext.getString(R.string.temperature))
                                .toString();

                        MainActivity.this.runOnUiThread(() -> {
                            state.setTemp(tempFromAPI);
                            adapter.notifyDataSetChanged();
                            stcConnector.update(state);

                            // Скрываем прогресс
                            progressBar.setVisibility(ProgressBar.GONE);
                        });

                    } catch (JSONException e) {
                        MainActivity.this.runOnUiThread(() -> {
                            state.setTemp("Ошибка данных");
                            adapter.notifyDataSetChanged();
                            stcConnector.update(state);
                            Toast.makeText(oContext, "Ошибка обработки данных", Toast.LENGTH_SHORT).show();

                            // Скрываем прогресс
                            progressBar.setVisibility(ProgressBar.GONE);
                        });
                    }
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                MainActivity.this.runOnUiThread(() -> {
                    state.setTemp(oContext.getString(R.string.err_connect));
                    adapter.notifyDataSetChanged();
                    stcConnector.update(state);
                    Toast.makeText(oContext, "Ошибка подключения", Toast.LENGTH_SHORT).show();

                    // Скрываем прогресс
                    progressBar.setVisibility(ProgressBar.GONE);
                });

                e.printStackTrace();
            }
        });
    }
}
