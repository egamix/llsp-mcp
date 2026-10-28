# Worked session: "Show my points as icons on a Lightspeed map"

A real session against LuciadLightspeed 2024.0. IDs are shortened.

## 1. Search for the task

```json
search_knowledge {"contractVersion":"read-tools/2","query":"show points as icons on a Lightspeed map","pageSize":6}
```
The hits include `EXAMPLE` records, for example
`samples/lightspeed/style/icon/LayerFactory.java` lines 86–109, plus `API` records. Pick the
sample whose path and title fit the task best.

## 2. Read the sample

```json
get_record {"contractVersion":"read-tools/2","recordIds":["<id of LayerFactory L86-109>"],"includeContent":true}
```
Title: `Example samples.lightspeed.style.icon.LayerFactory#createWorldIconPointLayer(ILcdModel)`,
summary "Creates and returns a layer for visualising points with fixed world sized icons", and the
code, which builds a `TLspIconStyle` with `TLspIconStyle.newBuilder().icon(icon)...`, then a layer with
`TLspShapeLayerBuilder.newBuilder()`, `.model(aModel)`, `.label(...)` and
`.bodyStyler(TLspPaintState.REGULAR, new TLspStyler(...))`, and finally `.build()`.

## 3. Check exact signatures you will use

```json
resolve_symbol {"contractVersion":"read-tools/2","symbol":"TLcdIconFactory#create"}
```
`AMBIGUOUS` with two overloads, `create(String)` and `create(String,Theme,Size)`. Pick one
deliberately; don't let the name alone decide.

## 4. Find more usage if needed

```json
get_relationships {"contractVersion":"read-tools/2","seed":"com.luciad.view.lightspeed.style.TLspIconStyle","direction":"IN","types":["CALLS","CONSTRUCTS","USES_TYPE"]}
```
Each edge's `sourceId` is another sample using the class; fetch a few with `get_record`.

## 5. Write the code, then verify every Luciad name

```json
check_symbols {"contractVersion":"read-tools/2","symbols":[
  "TLspShapeLayerBuilder#newBuilder()","TLspShapeLayerBuilder#model(ILcdModel)",
  "TLspIconStyle#newBuilder()","ILspView#addLayer(ILspLayer)","TLcdIconFactory#create"]}
```
The first four are `EXACT`. `TLcdIconFactory#create` comes back `AMBIGUOUS` and lists both
overloads; rewrite it as the overload the code actually calls, e.g.
`TLcdIconFactory#create(String,Theme,Size)`, and check again.

## 6. Answer

Present the code with citations: the sample file and line range it was adapted from, and the
Javadoc pages of the main types. State the served SDK version (`index_status` → `sdkVersion`) if the user's
version may differ, and say whether the code was compiled.
