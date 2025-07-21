import matplotlib.pyplot as plt
import numpy as np

SILU_DATA = {
    "SiLU1a":  {"MSE": 6.90e-4,  "area": 581.00, "color": "#366FC0", "marker": '*'},
    "SiLU1b":  {"MSE": 4.79e-4,  "area": 912.80, "color": "#366FC0", "marker": '*'},
    "SiLU1c":  {"MSE": 4.36e-4,  "area": 1388.24, "color": "#366FC0", "marker": '*'},
    "SiLU1d":  {"MSE": 3.94e-4,  "area": 903.28, "color": "#366FC0", "marker": '*'},
    "SiLU1e":  {"MSE": 6.81e-5,  "area": 1398.04, "color": "#366FC0", "marker": '*'},
    "SiLU1f":  {"MSE": 2.51e-5,  "area": 1912.12, "color": "#366FC0", "marker": '*'},
    "SiLU2a":  {"MSE": 3.52e-3,  "area": 1495.48, "color": "#9231C2", "marker": 's'},
    "SiLU2b":  {"MSE": 7.97e-4,  "area": 1722.56, "color": "#9231C2", "marker": 's'},
    "SiLU2c":  {"MSE": 4.50e-4,  "area": 1956.64, "color": "#9231C2", "marker": 's'},
    "SiLU3":   {"MSE": 4.86e-3,  "area": 1758.40, "color":  "#EF5048", "marker": '^'}, # TODO update the order in the text as well!
}

GELU_DATA = {
    "GELU1a":  {"MSE": 2.70e-4,  "area": 642.04, "color": "#366FC0", "marker": '*'},
    "GELU1b":  {"MSE": 4.99e-5,  "area": 946.68, "color": "#366FC0", "marker": '*'},
    "GELU1c":  {"MSE": 8.31e-6,  "area": 1371.16, "color": "#366FC0", "marker": '*'},
    "GELU1d":  {"MSE": 3.97e-4,  "area": 796.60, "color": "#366FC0", "marker": '*'},
    "GELU1e":  {"MSE": 4.99e-5,  "area": 1204.28, "color": "#366FC0", "marker": '*'},
    "GELU1f":  {"MSE": 8.31e-6,  "area": 1695.68, "color": "#366FC0", "marker": '*'},
    "GELU2a":  {"MSE": 1.03e-3,  "area": 1495.48, "color": "#9231C2", "marker": 's'},
    "GELU2b":  {"MSE": 3.38e-4,  "area": 1722.56, "color": "#9231C2", "marker": 's'},
    "GELU2c":  {"MSE": 2.76e-4,  "area": 1956.64, "color": "#9231C2", "marker": 's'},
}

DYT_DATA = {
    "DyT1a":  {"MSE": 3.72e-4,  "area": 1069.60, "color": "#366FC0", "marker": '*'},
    "DyT1b":  {"MSE": 7.56e-5,  "area": 1172.08, "color": "#366FC0", "marker": '*'},
    "DyT1c":  {"MSE": 2.63e-5,  "area": 1296.96 , "color": "#366FC0", "marker": '*'},
    "DyT1d":  {"MSE": 2.93e-4,  "area": 1120.00, "color": "#366FC0", "marker": '*'},
    "DyT1e":  {"MSE": 8.13e-5,  "area": 1212.96, "color": "#366FC0", "marker": '*'},
    "DyT1f":  {"MSE": 9.24e-5,  "area": 1309.28, "color": "#366FC0", "marker": '*'},
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
    ax.set_xlabel('Area [um²] (lower is better)', fontsize=16, labelpad=20)
    ax.set_ylabel('MSE (lower is better)', fontsize=16, labelpad=20)

    # add legend above the plot, box the legend, add texts: 1 is PPA, 2 is LUT, 3 is inverted Sigmoid LUT
    import matplotlib.patches as mpatches

    # Define legend groups and their descriptions
    legend_groups = [
        ("1", "#366FC0", '*', "direct LUT"),
        ("2", "#9231C2", 's', "Inverted Sigmoid LUT"),
        ("3", "#EF5048", '^', "PPA"),
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


def pareto_plot_allfunctions(func="SiLU", data=(SILU_DATA, GELU_DATA, DYT_DATA), xmin=0, xmax=2000, ymin=0, ymax=0.005, n_yticks=21):
    plot = plt.figure(figsize=(12, 10))
    plt.rcParams["font.family"] = "Times New Roman"
    plt.title(f"SiLU, GELU, DyT: Area versus MSE", fontsize=18, fontweight='bold', pad=50)
    ax = plt.gca() # Get current axes
    plt.xlim(xmin, xmax)
    plt.ylim(ymin, ymax)
    plt.xticks(fontsize=14)
    # Add more ticks for the y-axis in scientific notation
    yticks = np.linspace(ymin, ymax, n_yticks)
    plt.yticks(yticks, [f"{y:.1e}" for y in yticks], fontsize=14)

    # Names for each function for annotation
    func_names = ["SiLU", "GELU", "DyT"]

    # Scatter plot for data points from all functions
    for func_name, func_data in zip(func_names, data):
        areas = [v["area"] for v in func_data.values()]
        mses = [v["MSE"] for v in func_data.values()]
        labels = list(func_data.keys())
        colors = [v["color"] for v in func_data.values()]
        markers = [v["marker"] for v in func_data.values()]

        # Plot each point individually with its color and marker from data
        for area, mse, color, marker, label in zip(areas, mses, colors, markers, labels):
            ax.scatter(area, mse, color=color, s=80, marker=marker, zorder=3)
            ax.annotate(f"{label} ({func_name})", (area, mse), textcoords="offset points", xytext=(5,5), fontsize=12)

    # Make axes arrows
    arrowprops = dict(arrowstyle="->", linewidth=1.2, color='black', shrinkA=0, shrinkB=0)
    ax.annotate('', xy=(xmax, 0), xytext=(xmin, 0), arrowprops=arrowprops, clip_on=False)
    ax.annotate('', xy=(0, ymax), xytext=(0, ymin), arrowprops=arrowprops, clip_on=False)

    # Place axis labels at the center of each axis, just outside the plot area
    ax.set_xlabel('Area [um²] (lower is better)', fontsize=16, labelpad=20)
    ax.set_ylabel('MSE (lower is better)', fontsize=16, labelpad=20)

    # Define legend groups and their descriptions
    legend_groups = [
        ("1", "#366FC0", '*', "direct LUT"),
        ("2", "#9231C2", 's', "Inverted Sigmoid LUT"),
        ("3", "#EF5048", '^', "PPA"),
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


if __name__ == "__main__":
    # pareto_plot_1function(func="SiLU", data=SILU_DATA, xmin=0, xmax=2000, ymin=0, ymax=0.005, n_yticks=21)
    # pareto_plot(func="GELU", data=GELU_DATA, xmin=0, xmax=2000, ymin=0, ymax=0.0010, n_yticks=16)
    pareto_plot_allfunctions(func="SiLU", data=(SILU_DATA, GELU_DATA, DYT_DATA), xmin=0, xmax=2000, ymin=0, ymax=0.005, n_yticks=21)
