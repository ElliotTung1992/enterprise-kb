package com.github.elliot.kbdemo.flux;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class FluxDemo {

    public static void main(String[] args) throws InterruptedException {
        Flux.just("a", "b", "c", "d", "e")
                .concatWith(Flux.defer(() -> Flux.just("f", "g", "h")))
                .subscribe(System.out::println);
    }

    private void test1() throws InterruptedException {
        /* Flux.just("a", "b", "c")
                .concatMap(i -> Flux.just(i.toUpperCase(Locale.ROOT), i.concat("***"))
                        .delayElements(Duration.ofMillis("a".equals(i) ? 1000 : 500)))
                .subscribe(System.out::println);*/

        /*System.out.println("=============================");

        Flux.just("a", "b", "c")
                .flatMap(i -> Flux.just(i.toUpperCase(Locale.ROOT), i).
                        delayElements(Duration.ofMillis("a".equals(i) ? 1000 : 500)))
                .subscribe(System.out::println);*/

        Flux.just("a", "b", "c", "d", "e")
                .concatMap(s -> Flux.fromIterable(getList(s)))
                .subscribe(System.out::println);



        TimeUnit.SECONDS.sleep(10);
    }

    private static List<String> getList(String str) {
        return List.of(str, str.toUpperCase(Locale.ROOT));
    }
}
