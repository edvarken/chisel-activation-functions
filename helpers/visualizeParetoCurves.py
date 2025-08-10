import matplotlib.pyplot as plt
import numpy as np

SILU_DATA = {
    "SiLU1a":  {"MSE": 6.90e-4,  "area": 581.00, "color": "#366FC0", "marker": '*'},
    # "SiLU1b":  {"MSE": 4.79e-4,  "area": 912.80, "color": "#366FC0", "marker": '*'},
    # "SiLU1c":  {"MSE": 4.36e-4,  "area": 1388.24, "color": "#366FC0", "marker": '*'},
    "SiLU1d":  {"MSE": 3.94e-4,  "area": 903.28, "color": "#366FC0", "marker": '*'},
    "SiLU1e":  {"MSE": 6.81e-5,  "area": 1398.04, "color": "#366FC0", "marker": '*'},
    "SiLU1f":  {"MSE": 2.51e-5,  "area": 1912.12, "color": "#366FC0", "marker": '*'},
    "SiLU2a":  {"MSE": 3.52e-3,  "area": 1495.48, "color": "#366FC0", "marker": 's'},
    "SiLU2b":  {"MSE": 7.97e-4,  "area": 1722.56, "color": "#366FC0", "marker": 's'},
    "SiLU2c":  {"MSE": 4.50e-4,  "area": 1956.64, "color": "#366FC0", "marker": 's'},
    "SiLU3":   {"MSE": 3.62e-3,  "area": 1758.40, "color":  "#366FC0", "marker": '^'},
    # "SiLU4a":   {"MSE": 1.19e-4, "area": 3697.96, "color":  "#366FC0", "marker": 'o'},
    "SiLU4b":   {"MSE": 4.57e-5, "area": 3255.00, "color":  "#366FC0", "marker": 'o'},
    "SiLU5a":   {"MSE": 8.53e-5, "area": 2062.48, "color":  "#366FC0", "marker": '<'},
    "SiLU5b":   {"MSE": 7.23e-5, "area": 2133.88, "color":  "#366FC0", "marker": '<'},
}

GELU_DATA = {
    "GELU1a":  {"MSE": 2.77e-4,  "area": 642.04, "color": "#9231C2", "marker": '*'},
    "GELU1b":  {"MSE": 4.76e-5,  "area": 946.68, "color": "#9231C2", "marker": '*'},
    "GELU1c":  {"MSE": 7.99e-6,  "area": 1371.16, "color": "#9231C2", "marker": '*'},
    # "GELU1d":  {"MSE": 4.03e-4,  "area": 796.60, "color": "#9231C2", "marker": '*'},
    # "GELU1e":  {"MSE": 4.76e-5,  "area": 1204.28, "color": "#9231C2", "marker": '*'},
    # "GELU1f":  {"MSE": 7.99e-6,  "area": 1695.68, "color": "#9231C2", "marker": '*'},
    "GELU2a":  {"MSE": 9.37e-3,  "area": 1495.48, "color": "#9231C2", "marker": 's'},
    "GELU2b":  {"MSE": 3.25e-4,  "area": 1722.56, "color": "#9231C2", "marker": 's'},
    "GELU2c":  {"MSE": 2.55e-4,  "area": 1956.64, "color": "#9231C2", "marker": 's'},
    "GELU3":   {"MSE": 6.26e-4,  "area": 1758.40, "color":  "#9231C2", "marker": '^'},
    # "GELU4a":   {"MSE": 1.36e-4, "area": 3697.96, "color":  "#9231C2", "marker": 'o'},
    "GELU4b":   {"MSE": 9.25e-5, "area": 3255.00, "color":  "#9231C2", "marker": 'o'},
}

DYT_DATA = {
    "DyT1a":  {"MSE": 3.35e-4,  "area": 1069.60, "color": "#EF5048", "marker": '*'},
    "DyT1b":  {"MSE": 6.99e-5,  "area": 1172.08, "color": "#EF5048", "marker": '*'},
    "DyT1c":  {"MSE": 2.40e-5,  "area": 1296.96 , "color": "#EF5048", "marker": '*'},
    # "DyT1d":  {"MSE": 3.33e-4,  "area": 1120.00, "color": "#EF5048", "marker": '*'},
    # "DyT1e":  {"MSE": 6.92e-5,  "area": 1212.96, "color": "#EF5048", "marker": '*'},
    "DyT1f":  {"MSE": 1.85e-5,  "area": 1309.28, "color": "#EF5048", "marker": '*'},
}

def pareto_plot_1function(func="SiLU", data=SILU_DATA, xmin=0, xmax=2000, ymin=0, ymax=0.005, n_yticks=21):
    plot = plt.figure(figsize=(12, 10))
    plt.rcParams["font.family"] = "Times New Roman"
    plt.title(f"{func} versions: Area versus MSE", fontsize=18, fontweight='bold', pad=50)
    ax = plt.gca() # Get current axes
    plt.xlim(xmin, xmax)
    plt.ylim(ymin, ymax)
    plt.xticks(fontsize=14)
    # Add more ticks for the y-axis in scientific notation
    yticks = np.linspace(ymin, ymax, n_yticks)
    plt.yticks(yticks, [f"{y:.1e}" for y in yticks], fontsize=14)

    # Scatter plot for data points
    areas = [v["area"] for v in data.values()]
    mses = [v["MSE"] for v in data.values()]
    labels = list(data.keys())
    colors = [v["color"] for v in data.values()]
    markers = [v["marker"] for v in data.values()]

    # Plot each point individually with its color and marker from data
    for area, mse, color, marker in zip(areas, mses, colors, markers):
        ax.scatter(area, mse, color=color, s=80, marker=marker, zorder=3)

    # Annotate each point with its label
    for area, mse, label in zip(areas, mses, labels):
        ax.annotate(label, (area, mse), textcoords="offset points", xytext=(5,5), fontsize=12)

    # Make axes arrows
    arrowprops = dict(arrowstyle="->", linewidth=1.2, color='black', shrinkA=0, shrinkB=0)
    ax.annotate('', xy=(xmax, 0), xytext=(xmin, 0), arrowprops=arrowprops, clip_on=False)
    ax.annotate('', xy=(0, ymax), xytext=(0, ymin), arrowprops=arrowprops, clip_on=False)

    # Place axis labels at the center of each axis, just outside the plot area
    ax.set_xlabel('Area [um²] (lower is better)', fontsize=17, labelpad=25)
    ax.set_ylabel('MSE (lower is better)', fontsize=17, labelpad=18)

    # add legend above the plot, box the legend, add texts: 1 is PPA, 2 is LUT, 3 is inverted Sigmoid LUT
    import matplotlib.patches as mpatches

    # Define legend groups and their descriptions
    legend_groups = [
        ("1", '*', "zero-order direct"),
        ("2", 's', "zero-order Inverted Sigmoid"),
        ("3", '^', "h-SiLU/GELU"),
        ("4", 'o', "first-order Sigmoid"),
        ("5", '<', "first-order direct"),
    ]

    # Create handles for each group
    handles = [
        plt.Line2D([0], [0], marker=marker, color='w', label=f"{num}: {desc}",
                   markerfacecolor=color, markersize=12, markeredgecolor='k')
        for num, color, marker, desc in legend_groups
    ]

    # Place legend above the plot, boxed
    ax.legend(handles=handles, loc='lower center', bbox_to_anchor=(0.5, 1.02),
              fontsize=13, frameon=True, ncol=len(handles))

    plt.tight_layout()
    plt.show()


def pareto_plot_allfunctions(data=(SILU_DATA, GELU_DATA, DYT_DATA), xmin=0, xmax=2000, ymin=0, ymax=0.01, n_yticks=21, with_grid=False):
    plt.figure(figsize=(12, 8))
    plt.rcParams["font.family"] = "Times New Roman"
    # plt.title("MSE versus Area for SiLU, GELU, and DyT Variants", fontsize=18, fontweight='bold', pad=100)
    ax = plt.gca() # Get current axes
    plt.xlim(xmin, xmax)
    # Ensure ymin is positive for log scale
    ymin_log = ymin if ymin > 0 else 1e-6
    plt.ylim(ymin_log, ymax)
    plt.xticks(fontsize=20)
    # Add more ticks for the y-axis in logarithmic scale and scientific notation
    yticks = np.logspace(np.log10(ymin_log), np.log10(ymax), n_yticks)
    plt.yticks(yticks, [f"{y:.1e}" for y in yticks], fontsize=20)

    # Names for each function for annotation
    func_names = ["SiLU", "GELU", "DyT"]

    # Scatter plot for data points from all functions
    for func_name, func_data in zip(func_names, data):
        areas = [v["area"] for v in func_data.values()]
        mses = [v["MSE"] for v in func_data.values()]
        colors = [v["color"] for v in func_data.values()]
        markers = [v["marker"] for v in func_data.values()]

        # Plot each point individually with its color and marker from data
        for area, mse, color, marker in zip(areas, mses, colors, markers):
            ax.scatter(area, mse, color=color, s=120, marker=marker, zorder=3)

    # Make axes arrows
    arrowprops = dict(arrowstyle="->", linewidth=1.8, color='black', shrinkA=0, shrinkB=0)
    ax.annotate('', xy=(xmax, 0), xytext=(xmin, 0), arrowprops=arrowprops, clip_on=False, fontsize=22)
    ax.annotate('', xy=(0, ymax), xytext=(0, ymin), arrowprops=arrowprops, clip_on=False, fontsize=22)

    # Place axis labels at the center of each axis, just outside the plot area
    ax.set_xlabel('Area [um²]', fontsize=23, labelpad=5)
    ax.set_ylabel('MSE', fontsize=23, labelpad=5, rotation=0, ha='right', va='center')
    ax.set_yscale('log')  # Use logarithmic scale for y-axis

    # Define legend groups and their descriptions
    legend_groups = [
        ("SiLU1", "#366FC0", '*', "zero-order direct"),
        ("SiLU2", "#366FC0", 's', "zero-order Inv. Sigmoid"),
        ("SiLU3", "#366FC0", '^', "h-SiLU"),
        ("SiLU4", "#366FC0", 'o', "first-order Sigmoid"),
        ("SiLU5", "#366FC0", '<', "first-order direct"),
        ("GELU1", "#9231C2", '*', "zero-order direct"),
        ("GELU2", "#9231C2", 's', "zero-order Inv. Sigmoid"),
        ("GELU3", "#9231C2", '^', "h-GELU"),
        ("GELU4", "#9231C2", 'o', "first-order Sigmoid"),
        ("DyT", "#EF5048", '*', "zero-order direct"),
    ]

    # Create handles for each group
    handles = [
        plt.Line2D([0], [0], marker=marker, color='w', label=f"{num}: {desc}",
                   markerfacecolor=color, markersize=16, markeredgecolor='k')
        for num, color, marker, desc in legend_groups
    ]

    # Place legend above the plot, boxed, with more columns
    ax.legend(handles=handles, loc='lower center', bbox_to_anchor=(0.5, 1.02),
              fontsize=24, frameon=True, ncol=2)

    if with_grid:
        ax.yaxis.grid(True, which='both', linestyle='--', linewidth=1)
        ax.xaxis.grid(True, which='both', linestyle='--', linewidth=1)

    plt.tight_layout()
    plt.show()


if __name__ == "__main__":
    # pareto_plot_1function(func="SiLU", data=SILU_DATA, xmin=0, xmax=2000, ymin=0, ymax=0.005, n_yticks=21)
    # pareto_plot(func="GELU", data=GELU_DATA, xmin=0, xmax=2000, ymin=0, ymax=0.0010, n_yticks=16)
    pareto_plot_allfunctions(data=(SILU_DATA, GELU_DATA, DYT_DATA), xmin=500, xmax=4000, ymin=0, ymax=0.01, n_yticks=11, with_grid=True)
