# mrhyde

The primary design decision is to remove as much of the interop glue
and clj->js->clj data marshalling from application code as possible. 
`mrhyde` allows cljs sequential
types to be treated as native JavaScript arrays by implementing the
[ArrayLike.js specification](https://github.com/dribnet/ArrayLike.js)
and then using the ArrayLikeIsArray polyfill. This technique has
proven useful on libraries like leaflet and angular libraries. In addition,
`mrhyde` includes data interop glue so that cljs map types can be
treated as native JavaScript objects as well as several helpful
functions patching JavaScript functions to provide smoother interop.

See [strokes](https://github.com/dribnet/strokes) for an example of how
the mrhyde library is useful for JavaScript interop.

## Getting Started 

To use mrhyde from your ClojureScript project, 
add this dependency to your `project.clj`:

    [net.drib/mrhyde "0.5.0"]
