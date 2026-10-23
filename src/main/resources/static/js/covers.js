/* =========================================================================
   covers.js — the picture for an exercise: its uploaded cover, or else an
   illustration for its first topic (static/img/covers/<topic>.svg).
   ========================================================================= */

const ILLUSTRATED = new Set(["nature", "science", "history", "society", "technology"]);

/**
 * @param coverUrl  the uploaded cover, or null
 * @param topics    topic slugs, or {slug} objects, in display order
 */
export function coverFor(coverUrl, topics) {
    if (coverUrl) return coverUrl;
    const first = (topics || []).map((t) => (typeof t === "string" ? t : t.slug)).find((slug) => ILLUSTRATED.has(slug));
    return "/img/covers/" + (first || "default") + ".svg";
}

/** An <img> for the cover; decorative (the title next to it says what it is). */
export function coverImage(coverUrl, topics) {
    const img = document.createElement("img");
    img.src = coverFor(coverUrl, topics);
    img.alt = "";
    img.loading = "lazy";
    img.decoding = "async";
    return img;
}
