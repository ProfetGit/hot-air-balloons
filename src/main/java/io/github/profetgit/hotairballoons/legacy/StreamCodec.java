package io.github.profetgit.hotairballoons.legacy;

//? if <1.20.5 {
/*import com.mojang.datafixers.util.Function4;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

// Stands in for 1.20.5's StreamCodec (dev/renames.json points the imports here).
public interface StreamCodec<B, V> {
    V decode(B buf);

    void encode(B buf, V value);

    static <B, V> StreamCodec<B, V> of(BiConsumer<B, V> encoder, Function<B, V> decoder) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buf) {
                return decoder.apply(buf);
            }

            @Override
            public void encode(B buf, V value) {
                encoder.accept(buf, value);
            }
        };
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1, StreamCodec<? super B, T2> c2,
                                                      Function<C, T2> g2, BiFunction<T1, T2, C> make) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
        }, buf -> {
            T1 a = c1.decode(buf);
            T2 b = c2.decode(buf);
            return make.apply(a, b);
        });
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1, StreamCodec<? super B, T2> c2,
                                                              Function<C, T2> g2, StreamCodec<? super B, T3> c3, Function<C, T3> g3,
                                                              StreamCodec<? super B, T4> c4, Function<C, T4> g4, Function4<T1, T2, T3, T4, C> make) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
            c3.encode(buf, g3.apply(v));
            c4.encode(buf, g4.apply(v));
        }, buf -> {
            T1 a = c1.decode(buf);
            T2 b = c2.decode(buf);
            T3 c = c3.decode(buf);
            T4 d = c4.decode(buf);
            return make.apply(a, b, c, d);
        });
    }
}
*///?}
