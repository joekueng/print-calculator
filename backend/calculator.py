import trimesh
import sys

def calcola_volumi(volume_totale, superficie, wall_line_width=0.4, wall_line_count=3,
                   layer_height=0.2, infill_percentage=0.15):
    # Volume perimetrale stimato = superficie * spessore parete
    spessore_parete = wall_line_width * wall_line_count
    volume_pareti = superficie * spessore_parete

    # Volume interno (infill)
    volume_infill = (volume_totale - volume_pareti) * infill_percentage
    volume_effettivo = volume_pareti + max(volume_infill, 0)

    return volume_effettivo, volume_pareti, max(volume_infill, 0)

def calcola_peso(volume_mm3, densita_g_cm3=1.24):
    densita_g_mm3 = densita_g_cm3 / 1000
    return volume_mm3 * densita_g_mm3

def calcola_costo(peso_g, prezzo_kg=20.0):
    return round((peso_g / 1000) * prezzo_kg, 2)

def stima_tempo(volume_mm3 ):
    velocita_mm3_min = 0.4 *0.2 * 100 *60 # mm/s * mm * 60 s/min
    tempo_minuti = volume_mm3 / velocita_mm3_min
    return round(tempo_minuti, 1)

def main(percorso_stl):
    try:
        mesh = trimesh.load(percorso_stl)
        volume_modello = mesh.volume
        superficie = mesh.area

        volume_stampa, volume_pareti, volume_infill = calcola_volumi(
            volume_totale=volume_modello,
            superficie=superficie,
            wall_line_width=0.4,
            wall_line_count=3,
            layer_height=0.2,
            infill_percentage=0.15
        )

        peso = calcola_peso(volume_stampa)
        costo = calcola_costo(peso)
        tempo = stima_tempo(volume_stampa)

        print(f"Volume STL: {volume_modello:.2f} mm³")
        print(f"Superficie esterna: {superficie:.2f} mm²")
        print(f"Volume stimato pareti: {volume_pareti:.2f} mm³")
        print(f"Volume stimato infill: {volume_infill:.2f} mm³")
        print(f"Volume totale da stampare: {volume_stampa:.2f} mm³")
        print(f"Peso stimato: {peso:.2f} g")
        print(f"Costo stimato: CHF {costo}")
        print(f"Tempo stimato: {tempo} min")

    except Exception as e:
        print("Errore durante l'elaborazione:", e)

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Uso: python calcolatore_stl.py modello.stl")
    else:
        main(sys.argv[1])
