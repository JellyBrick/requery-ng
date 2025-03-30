module io.requery.kotlin {
    exports io.requery.kotlin;
    exports io.requery.async;
    exports io.requery.meta;
    exports io.requery.reactivex;
    exports io.requery.reactivex3;
    
    requires transitive io.requery;
    requires kotlin.stdlib;
    requires static io.reactivex.rxjava2;
    requires static io.reactivex.rxjava3;
    requires static reactor.core;
}
