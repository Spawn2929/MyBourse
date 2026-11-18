package fr.mybourse.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.mybourse.app.data.Point
import fr.mybourse.app.ui.theme.Couleurs

const val AVERTISSEMENT = "Outil personnel d'aide à l'analyse, pas un conseil en investissement."

/** Bandeau toujours visible au-dessus de la barre d'onglets. */
@Composable
fun Avertissement() {
    Text(
        AVERTISSEMENT,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Pastille ronde du score (0-100) : vert si élevé, orange si moyen, gris sinon. */
@Composable
fun PastilleScore(score: Double?, taille: Dp = 48.dp) {
    val cs = MaterialTheme.colorScheme
    val (fond, texte) = when {
        score == null -> cs.surfaceContainerHighest to cs.onSurfaceVariant
        score >= 70 -> cs.primary to cs.onPrimary
        score >= 50 -> Color(0xFFE0A030) to Color(0xFF2A1A00)
        else -> cs.surfaceContainerHighest to cs.onSurfaceVariant
    }
    Box(
        Modifier.size(taille).clip(CircleShape).background(fond),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            score?.let { Format.score(it) } ?: "–",
            color = texte,
            fontWeight = FontWeight.Bold,
            style = if (taille >= 48.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
        )
    }
}

/** Barre de 0 à 100 avec libellé et valeur (détail des signaux). */
@Composable
fun BarreNote(libelle: String, valeur: Double, aide: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(libelle, style = MaterialTheme.typography.bodyMedium)
            Text(Format.score(valeur) + " / 100", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { (valeur / 100).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            drawStopIndicator = {},
        )
        if (aide != null) {
            Text(aide, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Petit graphique sans axes, coloré selon la tendance de la période. */
@Composable
fun MiniCourbe(points: List<Point>, modifier: Modifier = Modifier.width(96.dp).height(40.dp)) {
    if (points.size < 2) return
    val couleur = Couleurs.signe(points.last().valeur - points.first().valeur)
    Canvas(modifier) {
        val min = points.minOf { it.valeur }
        val max = points.maxOf { it.valeur }
        val ecart = (max - min).takeIf { it > 0 } ?: 1.0
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = size.width * i / (points.size - 1)
            val y = size.height * (1 - ((p.valeur - min) / ecart)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, couleur, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Série d'un graphique comparatif (valeurs déjà ramenées sur la même base). */
data class Serie(val nom: String, val points: List<Point>, val couleur: Color, val pointillee: Boolean = false)

/** Graphique de cours avec plus haut / plus bas et dates de début et de fin. */
@Composable
fun Graphique(
    series: List<Serie>,
    modifier: Modifier = Modifier,
    legende: Boolean = true,
    formatValeur: (Double) -> String,
) {
    val tous = series.flatMap { it.points }
    if (tous.size < 2) {
        Text(
            "Pas assez d'historique pour tracer le graphique",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp),
        )
        return
    }
    val min = tous.minOf { it.valeur }
    val max = tous.maxOf { it.valeur }
    val debut = tous.minOf { it.date }
    val fin = tous.maxOf { it.date }
    val grille = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Text(formatValeur(max), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(180.dp).padding(vertical = 4.dp)) {
            val ecart = (max - min).takeIf { it > 0 } ?: 1.0
            val jours = (fin.toEpochDay() - debut.toEpochDay()).coerceAtLeast(1)
            val tirets = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            drawLine(grille, Offset(0f, 0f), Offset(size.width, 0f), pathEffect = tirets)
            drawLine(grille, Offset(0f, size.height), Offset(size.width, size.height), pathEffect = tirets)
            for (s in series) {
                if (s.points.size < 2) continue
                val path = Path()
                s.points.forEachIndexed { i, p ->
                    val x = size.width * (p.date.toEpochDay() - debut.toEpochDay()) / jours
                    val y = size.height * (1 - ((p.valeur - min) / ecart)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path, s.couleur,
                    style = Stroke(
                        width = 2.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = if (s.pointillee) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
                    ),
                )
            }
        }
        Text(formatValeur(min), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Format.dateCourte(debut), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Format.dateCourte(fin), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (series.size > 1 && legende) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (s in series) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(s.couleur))
                        Text(" " + s.nom, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** Ligne « libellé ............ valeur ». */
@Composable
fun LigneInfo(libelle: String, valeur: String, couleur: Color = Color.Unspecified, gras: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(libelle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false))
        Text(
            valeur, style = MaterialTheme.typography.bodyMedium, color = couleur,
            fontWeight = if (gras) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Carte arrondie standard. */
@Composable
fun Carte(modifier: Modifier = Modifier, contenu: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp)) { contenu() }
    }
}

/** Message d'état vide, centré. */
@Composable
fun EtatVide(titre: String, texte: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(titre, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            texte, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
        )
    }
}
