import io
import trimesh
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

def calculate_volumes(total_volume_mm3: float,
                      surface_area_mm2: float,
                      nozzle_width_mm: float = 0.4,
                      wall_line_count: int = 3,
                      layer_height_mm: float = 0.2,
                      infill_fraction: float = 0.15):
    wall_thickness_mm = nozzle_width_mm * wall_line_count
    wall_volume_mm3 = surface_area_mm2 * wall_thickness_mm
    infill_volume_mm3 = max(total_volume_mm3 - wall_volume_mm3, 0) * infill_fraction
    total_print_volume_mm3 = wall_volume_mm3 + infill_volume_mm3
    return total_print_volume_mm3, wall_volume_mm3, infill_volume_mm3

def calculate_weight(volume_mm3: float, density_g_cm3: float = 1.24):
    density_g_mm3 = density_g_cm3 / 1000.0
    return volume_mm3 * density_g_mm3

def calculate_cost(weight_g: float, price_per_kg: float = 20.0):
    return round((weight_g / 1000.0) * price_per_kg, 2)

def estimate_time(volume_mm3: float,
                  nozzle_width_mm: float = 0.4,
                  layer_height_mm: float = 0.2,
                  print_speed_mm_per_s: float = 100.0):
    volumetric_speed_mm3_per_min = nozzle_width_mm * layer_height_mm * print_speed_mm_per_s * 60.0
    return round(volume_mm3 / volumetric_speed_mm3_per_min, 1)

@app.post("/calculate/stl")
async def calculate_from_stl(file: UploadFile = File(...)):
    if not file.filename.lower().endswith(".stl"):
        raise HTTPException(status_code=400, detail="Please upload an STL file.")
    try:
        contents = await file.read()
        mesh = trimesh.load(io.BytesIO(contents), file_type="stl")
        model_volume_mm3 = mesh.volume
        model_surface_area_mm2 = mesh.area

        print_volume, wall_volume, infill_volume = calculate_volumes(
            total_volume_mm3=model_volume_mm3,
            surface_area_mm2=model_surface_area_mm2
        )

        weight_g = calculate_weight(print_volume)
        cost_chf = calculate_cost(weight_g)
        time_min = estimate_time(print_volume)

        return {
            "stl_volume_mm3": round(model_volume_mm3, 2),
            "surface_area_mm2": round(model_surface_area_mm2, 2),
            "wall_volume_mm3": round(wall_volume, 2),
            "infill_volume_mm3": round(infill_volume, 2),
            "print_volume_mm3": round(print_volume, 2),
            "weight_g": round(weight_g, 2),
            "cost_chf": cost_chf,
            "time_min": time_min
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
