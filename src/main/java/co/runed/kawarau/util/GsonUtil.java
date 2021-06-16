package co.runed.kawarau.util;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.ZonedDateTime;

public class GsonUtil
{
    public static Gson create()
    {
        ExclusionStrategy excludeStrategy = new ExclusionStrategy()
        {
            @Override
            public boolean shouldSkipClass(Class<?> clazz)
            {
                return false;
            }

            @Override
            public boolean shouldSkipField(FieldAttributes field)
            {
                return field.getAnnotation(JsonExclude.class) != null;
            }
        };

        return new GsonBuilder()
                .setExclusionStrategies(excludeStrategy)
                .registerTypeAdapter(ZonedDateTime.class, new TypeAdapter<ZonedDateTime>()
                {
                    @Override
                    public void write(JsonWriter out, ZonedDateTime value) throws IOException
                    {
                        out.value(value == null ? null : value.toString());
                    }

                    @Override
                    public ZonedDateTime read(JsonReader in) throws IOException
                    {
                        return ZonedDateTime.parse(in.nextString());
                    }
                })
                .enableComplexMapKeySerialization()
                .create();
    }
}
