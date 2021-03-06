package com.example.bianca.caloriecounter.net;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import com.example.bianca.caloriecounter.R;
import com.example.bianca.caloriecounter.content.Aliment;
import com.example.bianca.caloriecounter.content.User;
import com.example.bianca.caloriecounter.mapping.AlimentReader;
import com.example.bianca.caloriecounter.mapping.CredentialWriter;
import com.example.bianca.caloriecounter.mapping.ResourceListReader;
import com.example.bianca.caloriecounter.mapping.TokenReader;
import com.example.bianca.caloriecounter.util.Cancellable;
import com.example.bianca.caloriecounter.util.OnErrorListener;
import com.example.bianca.caloriecounter.util.OnSuccessListener;
import com.example.bianca.caloriecounter.util.ResourceException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


/**
 * Created by bianca on 23.11.2016.
 */
public class AlimentRestClient {
    private static final String TAG = AlimentRestClient.class.getSimpleName();
    public static final String APPLICATION_JSON = "application/json";
    public static final String UTF_8 = "UTF-8";
    public static final String LAST_MODIFIED = "Last-Modified";

    private final OkHttpClient okHttpClient;
    private final String apiUrl;
    private final String alimentUrl;
    private final Context context;
    private final String authUrl;
    private final String signupUrl;
    private Socket socket;
    private User user;
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    public AlimentRestClient(Context cont) {
        this.context = cont;
        okHttpClient = new OkHttpClient();
        apiUrl = context.getString(R.string.api_url);
        alimentUrl = apiUrl.concat("/aliment");
        authUrl = apiUrl.concat("/api/auth");
        signupUrl = apiUrl.concat("/api/signup");
        Log.d(TAG, "AlimentRestClient created");
    }

    public CancellableOkHttpAsync<String> getToken(User user, OnSuccessListener<String> onSuccessListener, OnErrorListener errorListener) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        JsonWriter jsonWriter = null;
        try {
            jsonWriter = new JsonWriter(new OutputStreamWriter(byteArrayOutputStream, UTF_8));
            new CredentialWriter().write(user, jsonWriter);
            jsonWriter.close();
        } catch (Exception e) {
            Log.e(TAG, "Cannot get token", e);
            throw new ResourceException(e);
        }
        Log.d(TAG, authUrl + "/session");
        return new CancellableOkHttpAsync<>(
                new Request.Builder().url(authUrl + "/session")
                        .post(RequestBody.create(MediaType.parse(APPLICATION_JSON), byteArrayOutputStream.toByteArray()))
                        .build(),
                new ResponseReader<String>() {
                    @Override
                    public String read(Response response) throws Exception {
                        JsonReader jsonReader = new JsonReader(new InputStreamReader(response.body().byteStream(), UTF_8));
                        if (response.code() == 201) {
                            //created
                            return new TokenReader().read(jsonReader);
                        } else {
                            return null;
                        }
                    }
                },
                onSuccessListener,
                errorListener
        );
    }

    public Cancellable readAsync(String name, final OnSuccessListener<Aliment> successListener,
                                 final OnErrorListener onErrorListener) {
        Request.Builder builder = new Request.Builder().url(String.format("%s/%s", alimentUrl, name));
        addAuthToken(builder);
        return new CancellableOkHttpAsync<Aliment>(
                builder.build(),
                new ResponseReader<Aliment>() {
                    @Override
                    public Aliment read(Response response) throws Exception {
                        if (response.code() == 200) {
                            JsonReader reader = new JsonReader(new InputStreamReader(response.body().byteStream(), UTF_8));
                            return new AlimentReader().read(reader);
                        } else { //404 not found
                            return null;
                        }
                    }
                },
                successListener,
                onErrorListener
        );
    }

    private void addAuthToken(Request.Builder builder) {
        User u = user;
        Log.d(TAG, "Add token to request "+user.getToken());
        if (u != null) {
            builder.header("Authorization", "Bearer " + user.getToken());
        }
    }

    public void setUser(User user) {
        this.user = user;
    }

    public CancellableOkHttpAsync<List<Aliment>> searchAsync(String alimentsLastUpdate, OnSuccessListener<List<Aliment>> onSuccessListener, OnErrorListener onErrorListener) {
        Request.Builder requestBuilder = new Request.Builder().url(alimentUrl);
        if (alimentsLastUpdate != null) {
            requestBuilder.header(LAST_MODIFIED, alimentsLastUpdate);
        }
        addAuthToken(requestBuilder);
        return new CancellableOkHttpAsync<List<Aliment>>(
                requestBuilder.build(),
                new ResponseReader<List<Aliment>>() {
                    @Override
                    public List<Aliment> read(Response response) throws Exception {
                        JsonReader reader = new JsonReader(new InputStreamReader(response.body().byteStream(), UTF_8));
                        List<Aliment> result =
                                new ResourceListReader<Aliment>(new AlimentReader()).read(reader);
                        Log.d(TAG, String.valueOf(result.size()));
                        return result;

                    }
                },
                onSuccessListener,
                onErrorListener
        );
    }

    public Cancellable deleteAsync(String name, OnSuccessListener<Aliment> onSuccessListener, OnErrorListener onErrorListener) {
        Request.Builder builder = new Request.Builder().url(String.format("%s/%s", alimentUrl, name));
        JSONObject jsonObject= new JSONObject();
        try {
            jsonObject.put("name", name);
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        RequestBody body = RequestBody.create(JSON, jsonObject.toString());
        builder.method("DELETE", body);
        addAuthToken(builder);
        return new CancellableOkHttpAsync<Aliment>(
                builder.build(),
                new ResponseReader<Aliment>() {
                    @Override
                    public Aliment read(Response response) throws Exception {
                        if (response.code() == 200) {
                            JsonReader reader = new JsonReader(new InputStreamReader(response.body().byteStream(), UTF_8));
                            return new AlimentReader().read(reader);
                        } else { //404 not found
                            return null;
                        }
                    }
                },
                onSuccessListener,
                onErrorListener
        );
    }

    public Cancellable saveAsync(Aliment aliment, boolean update, OnSuccessListener<Aliment> onSuccessListener, OnErrorListener onErrorListener) {
        Request.Builder builder = new Request.Builder().url(String.format("%s/%s", alimentUrl, aliment.getName()));
        addAuthToken(builder);
        Log.d(TAG,aliment.toJsonString());
        RequestBody body = RequestBody.create(JSON, aliment.toJsonString());
        if (update) {
            builder.method("PUT", body);
            Log.d(TAG, "PUT methd");
        }else{
            builder = new Request.Builder().url(String.format("%s", alimentUrl));
            builder.method("POST", body);
            Log.d(TAG, "POST methd");
        }
        return new CancellableOkHttpAsync<Aliment>(
                builder.build(),
                new ResponseReader<Aliment>() {
                    @Override
                    public Aliment read(Response response) throws Exception {
                        Log.d(TAG, String.valueOf(response.code()));
                        if (response.code() == 200) {
                            JsonReader reader = new JsonReader(new InputStreamReader(response.body().byteStream(), UTF_8));
                            return new AlimentReader().read(reader);
                        } else { //404 not found
                            return null;
                        }
                    }
                },
                onSuccessListener,
                onErrorListener
        );
    }

    private static interface ResponseReader<E> {
        E read(Response response) throws Exception;
    }

    private class CancellableOkHttpAsync<E> implements Cancellable {
        private Call call;

        public CancellableOkHttpAsync(final Request req, final ResponseReader<E> responseReader,
                                      final OnSuccessListener<E> onSuccessListener, final OnErrorListener onErrorListener) {
            try {
                call = okHttpClient.newCall(req);
                Log.d(TAG, "started " + req.method() + " " + req.url());
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        notifyFailure(e, req, onErrorListener);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try {
                            //Log.d(TAG, response.body().string());
                            notifySuccess(response, req, onSuccessListener, responseReader);
                        } catch (Exception e) {
                            notifyFailure(e, req, onErrorListener);
                        }
                    }
                });
            } catch (Exception e) {
                Log.d(TAG, e.toString());
                notifyFailure(e, req, onErrorListener);
            }
        }

        @Override
        public void cancel() {
            if (call != null) {
                call.cancel();
            }
        }

        private void notifySuccess(Response response, Request request,
                                   OnSuccessListener<E> successListener, ResponseReader<E> responseReader) throws Exception {
            if (call.isCanceled()) {
                Log.d(TAG, "completed, but cancelled " + request.method() + " " + request.url());
            } else {
                Log.d(TAG, "completed " + request.method() + " " + request.url());
                successListener.onSuccess(responseReader.read(response));
            }
        }

        private void notifyFailure(Exception e, Request request, OnErrorListener errorListener) {
            if (call.isCanceled()) {
                Log.d(TAG, "failed, but cancelled " + request.method() + " " + request.url());
            } else {
                Log.d(TAG, "failed  " + request.method() + " " + request.url());
                errorListener.onError(e instanceof ResourceException ? e : new ResourceException(e));
            }
        }
    }
}
