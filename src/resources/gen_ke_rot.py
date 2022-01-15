import os
import math
"""Generates rotated/transformed versions of the KE matrix for all 8 iteration values.
These rotations of the KE matrix are required to properly execute matrix-vector products, since the coordinate transform
applied to DOFs requires a likewise coordinate transform to the KE matrix to still generate correct outputs"""


"""
With the below mappings, we can generate a transformed version of KE for each of the 8 possible iteration values.
If eg. the iteration value is 0 (first row), the first 3 columns of KE (starting with 0.235, 0.080, 0.080) get mapped to columns 1,9 and 17 of the transformed matrix.
Likewise, the next 3 colums (starting with -0.106, 0.016, 0.016) get mapped to columns 5, 13 and 21 of the transformed matrix.
This transformation is performed for all 8 possible iteration values.
Once columns have reordered, the elements in each column are also transformed, moving elements (0,1,2) in column N to position (1,9,17), (3,4,5) to (5,13,21), etc
"""
map = [
  [1, 5, 4, 0, 3, 7, 6, 2],
  [0, 4, 5, 1, 2, 6, 7, 3],
  [3, 7, 6, 2, 1, 5, 4, 0],
  [2, 6, 7, 3, 0, 4, 5, 1],
  [5, 1, 0, 4, 7, 3, 2, 6],
  [4, 0, 1, 5, 6, 2, 3, 7],
  [7, 3, 2, 6, 5, 1, 0, 4],
  [6, 2, 3, 7, 4, 0, 1, 5]
]


ke = [
[0.235043,0.080128,0.080128,-0.106838,0.016026,0.016026,-0.08547,-0.080128,0.008013,0.053419,-0.016026,0.040064,0.053419,0.040064,-0.016026,-0.08547,0.008013,-0.080128,-0.058761,-0.040064,-0.040064,-0.005342,-0.008013,-0.008013],
[0.080128,0.235043,0.080128,-0.016026,0.053419,0.040064,-0.080128,-0.08547,0.008013,0.016026,-0.106838,0.016026,0.040064,0.053419,-0.016026,-0.008013,-0.005342,-0.008013,-0.040064,-0.058761,-0.040064,0.008013,-0.08547,-0.080128],
[0.080128,0.080128,0.235043,-0.016026,0.040064,0.053419,-0.008013,-0.008013,-0.005342,0.040064,-0.016026,0.053419,0.016026,0.016026,-0.106838,-0.080128,0.008013,-0.08547,-0.040064,-0.040064,-0.058761,0.008013,-0.080128,-0.08547],
[-0.106838,-0.016026,-0.016026,0.235043,-0.080128,-0.080128,0.053419,0.016026,-0.040064,-0.08547,0.080128,-0.008013,-0.08547,-0.008013,0.080128,0.053419,-0.040064,0.016026,-0.005342,0.008013,0.008013,-0.058761,0.040064,0.040064],
[0.016026,0.053419,0.040064,-0.080128,0.235043,0.080128,-0.016026,-0.106838,0.016026,0.080128,-0.08547,0.008013,0.008013,-0.005342,-0.008013,-0.040064,0.053419,-0.016026,-0.008013,-0.08547,-0.080128,0.040064,-0.058761,-0.040064],
[0.016026,0.040064,0.053419,-0.080128,0.080128,0.235043,-0.040064,-0.016026,0.053419,0.008013,-0.008013,-0.005342,0.080128,0.008013,-0.08547,-0.016026,0.016026,-0.106838,-0.008013,-0.080128,-0.08547,0.040064,-0.040064,-0.058761],
[-0.08547,-0.080128,-0.008013,0.053419,-0.016026,-0.040064,0.235043,0.080128,-0.080128,-0.106838,0.016026,-0.016026,-0.058761,-0.040064,0.040064,-0.005342,-0.008013,0.008013,0.053419,0.040064,0.016026,-0.08547,0.008013,0.080128],
[-0.080128,-0.08547,-0.008013,0.016026,-0.106838,-0.016026,0.080128,0.235043,-0.080128,-0.016026,0.053419,-0.040064,-0.040064,-0.058761,0.040064,0.008013,-0.08547,0.080128,0.040064,0.053419,0.016026,-0.008013,-0.005342,0.008013],
[0.008013,0.008013,-0.005342,-0.040064,0.016026,0.053419,-0.080128,-0.080128,0.235043,0.016026,-0.040064,0.053419,0.040064,0.040064,-0.058761,-0.008013,0.080128,-0.08547,-0.016026,-0.016026,-0.106838,0.080128,-0.008013,-0.08547],
[0.053419,0.016026,0.040064,-0.08547,0.080128,0.008013,-0.106838,-0.016026,0.016026,0.235043,-0.080128,0.080128,-0.005342,0.008013,-0.008013,-0.058761,0.040064,-0.040064,-0.08547,-0.008013,-0.080128,0.053419,-0.040064,-0.016026],
[-0.016026,-0.106838,-0.016026,0.080128,-0.08547,-0.008013,0.016026,0.053419,-0.040064,-0.080128,0.235043,-0.080128,-0.008013,-0.08547,0.080128,0.040064,-0.058761,0.040064,0.008013,-0.005342,0.008013,-0.040064,0.053419,0.016026],
[0.040064,0.016026,0.053419,-0.008013,0.008013,-0.005342,-0.016026,-0.040064,0.053419,0.080128,-0.080128,0.235043,0.008013,0.080128,-0.08547,-0.040064,0.040064,-0.058761,-0.080128,-0.008013,-0.08547,0.016026,-0.016026,-0.106838],
[0.053419,0.040064,0.016026,-0.08547,0.008013,0.080128,-0.058761,-0.040064,0.040064,-0.005342,-0.008013,0.008013,0.235043,0.080128,-0.080128,-0.106838,0.016026,-0.016026,-0.08547,-0.080128,-0.008013,0.053419,-0.016026,-0.040064],
[0.040064,0.053419,0.016026,-0.008013,-0.005342,0.008013,-0.040064,-0.058761,0.040064,0.008013,-0.08547,0.080128,0.080128,0.235043,-0.080128,-0.016026,0.053419,-0.040064,-0.080128,-0.08547,-0.008013,0.016026,-0.106838,-0.016026],
[-0.016026,-0.016026,-0.106838,0.080128,-0.008013,-0.08547,0.040064,0.040064,-0.058761,-0.008013,0.080128,-0.08547,-0.080128,-0.080128,0.235043,0.016026,-0.040064,0.053419,0.008013,0.008013,-0.005342,-0.040064,0.016026,0.053419],
[-0.08547,-0.008013,-0.080128,0.053419,-0.040064,-0.016026,-0.005342,0.008013,-0.008013,-0.058761,0.040064,-0.040064,-0.106838,-0.016026,0.016026,0.235043,-0.080128,0.080128,0.053419,0.016026,0.040064,-0.08547,0.080128,0.008013],
[0.008013,-0.005342,0.008013,-0.040064,0.053419,0.016026,-0.008013,-0.08547,0.080128,0.040064,-0.058761,0.040064,0.016026,0.053419,-0.040064,-0.080128,0.235043,-0.080128,-0.016026,-0.106838,-0.016026,0.080128,-0.08547,-0.008013],
[-0.080128,-0.008013,-0.08547,0.016026,-0.016026,-0.106838,0.008013,0.080128,-0.08547,-0.040064,0.040064,-0.058761,-0.016026,-0.040064,0.053419,0.080128,-0.080128,0.235043,0.040064,0.016026,0.053419,-0.008013,0.008013,-0.005342],
[-0.058761,-0.040064,-0.040064,-0.005342,-0.008013,-0.008013,0.053419,0.040064,-0.016026,-0.08547,0.008013,-0.080128,-0.08547,-0.080128,0.008013,0.053419,-0.016026,0.040064,0.235043,0.080128,0.080128,-0.106838,0.016026,0.016026],
[-0.040064,-0.058761,-0.040064,0.008013,-0.08547,-0.080128,0.040064,0.053419,-0.016026,-0.008013,-0.005342,-0.008013,-0.080128,-0.08547,0.008013,0.016026,-0.106838,0.016026,0.080128,0.235043,0.080128,-0.016026,0.053419,0.040064],
[-0.040064,-0.040064,-0.058761,0.008013,-0.080128,-0.08547,0.016026,0.016026,-0.106838,-0.080128,0.008013,-0.08547,-0.008013,-0.008013,-0.005342,0.040064,-0.016026,0.053419,0.080128,0.080128,0.235043,-0.016026,0.040064,0.053419],
[-0.005342,0.008013,0.008013,-0.058761,0.040064,0.040064,-0.08547,-0.008013,0.080128,0.053419,-0.040064,0.016026,0.053419,0.016026,-0.040064,-0.08547,0.080128,-0.008013,-0.106838,-0.016026,-0.016026,0.235043,-0.080128,-0.080128],
[-0.008013,-0.08547,-0.080128,0.040064,-0.058761,-0.040064,0.008013,-0.005342,-0.008013,-0.040064,0.053419,-0.016026,-0.016026,-0.106838,0.016026,0.080128,-0.08547,0.008013,0.016026,0.053419,0.040064,-0.080128,0.235043,0.080128],
[-0.008013,-0.080128,-0.08547,0.040064,-0.040064,-0.058761,0.080128,0.008013,-0.08547,-0.016026,0.016026,-0.106838,-0.040064,-0.016026,0.053419,0.008013,-0.008013,-0.005342,0.016026,0.040064,0.053419,-0.080128,0.080128,0.235043]
]

kenew = [[[0 for j in range(24)] for i in range(24)] for k in range(8)] # Create 8 empty 24x24 matrices

# Change order of columns
for iter in range(8):
  for r in range(24):
    for c in range(8):
      for j in [0, 8, 16]:
        t = ke[r][int(j/8)+c*3]
        kenew[iter][r][map[iter][c]+j] = t


# for iter in range(8):
#   f = open(f"{p}/ke-{iter}.csv", "w")
#   for r in range(24):
#     for c in range(24):
#       f.write(str(kenew[iter][r][c]))
#       if(c != 23):
#         f.write(",")
#       else:
#         f.write("\n")
#   f.close()

kenew_new = [[[0 for j in range(24)] for i in range(24)] for k in range(8)] # Create another 8 empty 24x24 matrices
# Change order of items in each column
for iter in range(8):
  for c in range(24):
    for r in range(8):
      for j in [0, 8, 16]:
        t = kenew[iter][int(j/8)+r*3][c]
        kenew_new[iter][map[iter][r]+j][c] = t

#Write to csv file
p = os.path.dirname(os.path.abspath(__file__))
for iter in range(8):
  f = open(f"{p}/ke-{iter}.csv", "w")
  for r in range(24):
    for c in range(24):
      f.write(str(kenew_new[iter][r][c]))
      if(c != 23):
        f.write(",")
      else:
        f.write("\n")


# Verify that KE0/7, KE1/6, KE2/5 and KE3/4 are the same
for iter in range(8):
  i1 = iter
  i2 = 7 - iter
  print(f"ITER: {iter}")
  for r in range(24):
    for c in range(24):
      v1 = kenew_new[i1][r][c]
      v2 = kenew_new[i2][r][c]
      if not math.isclose(v1, v2):
        print(f"ERR: r={r},c={c}, v1={v1}, v2={v2}")
        exit(0)
