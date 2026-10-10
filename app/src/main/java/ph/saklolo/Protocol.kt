package ph.saklolo

import org.json.JSONObject

// Pure engine: no Android imports (org.json comes from the platform, or a test dep on the JVM).

data class Txt(val en: String, val fil: String)

enum class Ans { YES, NO, UNSURE }
enum class Hazard { COLLAPSE, FLOOD, LANDSLIDE, OTHER }
enum class Cond { NOT_BREATHING, UNCONSCIOUS, HEAVY_BLEEDING, PINNED, INJURED, WATER_RISING }

// fact: a question step whose YES (or UNSURE, the safer reading) is also a fact for rescuers ("Is the water still rising?").
// roof: the step shown instead when the victim is already on the roof ("go higher" makes no sense there).
data class Node(
    val id: String, val text: Txt, val detail: Txt?,
    val next: String?, val yes: String?, val no: String?, val safer: String?, val fact: Cond? = null, val roof: String? = null,
) {
    val isQuestion get() = yes != null
    fun after(a: Ans): String? = when (a) { Ans.YES -> yes; Ans.NO -> no; Ans.UNSURE -> safer }
}

// Approved myth answer: written in advance, never by the model. The model only picks its id (or NONE).
data class Faq(val id: String, val examples: List<String>, val answer: Txt)

data class Protocol(val id: String, val title: Txt, val nodes: List<Node>, val faqs: List<Faq> = emptyList()) {
    fun node(id: String) = nodes.first { it.id == id }

    // The step to show given what's already known: the roof version on a roof, and a question whose fact was
    // already reported is answered YES instead of asked again.
    fun resolve(id: String, f: Facts): String {
        var n = node(id)
        while (true) n = when {
            n.roof != null && f.floor == ROOF -> node(n.roof)
            n.isQuestion && n.fact != null && n.fact in f.cond -> node(n.yes!!)
            else -> return n.id
        }
    }
}

class Protocols(json: String) {
    private val root = JSONObject(json)

    // Fixed follow-up questions by id (see followUps() in Triage.kt), with optional wording per hazard ("by_hazard").
    val followups: Map<String, Txt> = root.objects("followups").associate { it.getString("id") to it.txt("text")!! }
    val followupsByHazard: Map<Pair<String, Hazard>, Txt> = root.objects("followups").flatMap { f ->
        f.optJSONObject("by_hazard")?.let { h -> h.keys().asSequence().map { (f.getString("id") to Hazard.valueOf(it)) to h.txt(it)!! }.toList() }.orEmpty()
    }.toMap()
    fun followup(id: String, hazard: Hazard?): Txt = hazard?.let { followupsByHazard[id to it] } ?: followups.getValue(id)

    // Input-side example phrases per label (group → label → phrases) for the embedding model; never shown to users.
    val examples: Map<String, Map<String, List<String>>> = root.optJSONObject("examples")?.let { g ->
        g.keys().asSequence().associateWith { k ->
            g.getJSONObject(k).let { l -> l.keys().asSequence().associateWith { x -> l.getJSONArray(x).let { a -> (0 until a.length()).map(a::getString) } } }
        }
    }.orEmpty()

    val all: Map<String, Protocol> = root.getJSONArray("protocols").let { arr ->
        (0 until arr.length()).map { arr.getJSONObject(it) }.associate { p ->
            val nodes = p.getJSONArray("nodes")
            p.getString("id") to Protocol(p.getString("id"), p.txt("title")!!, (0 until nodes.length()).map {
                val n = nodes.getJSONObject(it)
                Node(n.getString("id"), n.txt("text")!!, n.txt("detail"), n.str("next"), n.str("yes"), n.str("no"), n.str("safer"), n.str("fact")?.let(Cond::valueOf), n.str("roof"))
            }, p.objects("faqs").map { f ->
                Faq(f.getString("id"), f.getJSONArray("q_examples").let { a -> (0 until a.length()).map { a.getString(it) } }, f.txt("answer")!!)
            })
        }
    }

    // Life threats first (CPR, then bleeding), then the hazard protocol. Each entry is (protocol, entry node).
    fun plan(hazard: Hazard?, conds: Set<Cond>): List<Pair<Protocol, String>> = buildList {
        if (Cond.NOT_BREATHING in conds) add(all.getValue("NOT_BREATHING") to if (hazard == Hazard.FLOOD) "nb_breaths" else "nb_push")
        if (Cond.HEAVY_BLEEDING in conds) add(first("BLEEDING"))
        when (hazard) {
            Hazard.COLLAPSE, Hazard.LANDSLIDE -> add(first("COLLAPSE"))
            Hazard.FLOOD -> add(first("ROOF"))
            else -> {}
        }
    }

    private fun first(id: String) = all.getValue(id).let { it to it.nodes.first().id }
}

private fun JSONObject.objects(k: String) = optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
private fun JSONObject.str(k: String) = if (has(k)) getString(k) else null
private fun JSONObject.txt(k: String) = optJSONObject(k)?.let { Txt(it.getString("en"), it.getString("fil")) }
