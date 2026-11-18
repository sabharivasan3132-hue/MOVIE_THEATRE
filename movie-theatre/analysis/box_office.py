from getpass import getpass
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd

def chart(series, title, ylabel, filename, output, kind):
    ax = series.plot(kind=kind, figsize=(8, 4), color="#245486", rot=20)
    ax.set(title=title, ylabel=ylabel, xlabel="")
    ax.figure.tight_layout()
    ax.figure.savefig(output / filename, dpi=150)
    plt.close(ax.figure)

def generate(data, output):
    data = data.copy()
    data["starts_at"] = pd.to_datetime(data["starts_at"], errors="coerce")
    for column in ("booked", "capacity", "revenue"):
        data[column] = pd.to_numeric(data[column], errors="coerce")
    data = data.dropna().drop_duplicates(subset="id")
    data = data[data["capacity"] > 0].copy()
    if data.empty:
        return 0
    data["month"] = data["starts_at"].dt.to_period("M").astype(str)
    output.mkdir(parents=True, exist_ok=True)
    chart(data.groupby("title")["booked"].sum().sort_values(ascending=False),
          "Movie Popularity", "Confirmed tickets", "movie_popularity.png", output, "bar")
    months = data.groupby("month")[["booked", "capacity", "revenue"]].sum()
    chart(100 * months["booked"] / months["capacity"], "Monthly Occupancy",
          "Booked seats (%)", "occupancy_trend.png", output, "line")
    chart(months["revenue"], "Monthly Net Box Office Revenue",
          "INR after refunds", "revenue_trend.png", output, "bar")
    return len(data)

def main():
    import psycopg
    with psycopg.connect(host="localhost", port=5432, dbname="movie_theatre",
                         user="postgres", password=getpass("PostgreSQL password: ")) as conn:
        with conn.cursor() as cursor:
            cursor.execute("SELECT id,title,starts_at,booked,capacity,revenue FROM show_summary ORDER BY starts_at,id")
            data = pd.DataFrame(cursor.fetchall(), columns=[col.name for col in cursor.description])
    count = generate(data, Path(__file__).resolve().parent / "output")
    print(f"Saved three charts from {count} shows." if count else "No usable shows found.")

if __name__ == "__main__":
    main()
