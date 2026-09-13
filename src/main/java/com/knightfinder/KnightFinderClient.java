package com.knightfinder;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class KnightFinderClient
{
	private static final HttpUrl KNIGHTS_URL = HttpUrl.get("https://knights.shep.rip/api/v1/knights");
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final Type SPLIT_LIST = new TypeToken<List<KnightSplit>>() { }.getType();
	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	KnightFinderClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * @param fresh asks any cache between here and the server for a new copy, for the moment right
	 *              after this client changed something.
	 */
	void list(boolean fresh, Consumer<List<KnightSplit>> success, Consumer<String> failure)
	{
		Request.Builder builder = new Request.Builder().url(KNIGHTS_URL).get();
		if (fresh) builder.header("Cache-Control", "no-cache");
		httpClient.newCall(builder.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				failure.accept("Knight server unavailable");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response closeable = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						failure.accept("Knight server returned " + response.code());
						return;
					}
					List<KnightSplit> splits = gson.fromJson(response.body().charStream(), SPLIT_LIST);
					success.accept(splits == null ? Collections.emptyList() : splits);
				}
				catch (RuntimeException exception)
				{
					failure.accept("Invalid knight response");
				}
			}
		});
	}

	void report(KnightSplit split, Runnable success, Consumer<String> failure)
	{
		Request request = new Request.Builder().url(KNIGHTS_URL)
			.post(RequestBody.create(JSON, gson.toJson(split))).build();
		execute(request, success, failure);
	}

	void delete(int world, Runnable success, Consumer<String> failure)
	{
		HttpUrl url = KNIGHTS_URL.newBuilder().addPathSegment(Integer.toString(world)).build();
		execute(new Request.Builder().url(url).delete().build(), success, failure);
	}

	private void execute(Request request, Runnable success, Consumer<String> failure)
	{
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				failure.accept("Knight server unavailable");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response closeable = response)
				{
					if (response.isSuccessful()) success.run();
					else failure.accept("Knight server returned " + response.code());
				}
			}
		});
	}
}
