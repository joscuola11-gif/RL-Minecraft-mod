package com.rlfennec.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rlfennec.physics.Arena;
import org.junit.jupiter.api.Test;

class ArenaMeshTest {
    private static double worstDistance(ArenaMesh.Mesh mesh) {
        double worst = 0.0D;
        for (int i = 0; i < mesh.vertexCount; i++) {
            int o = i * ArenaMesh.FLOATS_PER_VERTEX;
            worst = Math.max(worst, Math.abs(Arena.sd(mesh.data[o], mesh.data[o + 1], mesh.data[o + 2])));
        }
        return worst;
    }

    @Test
    void everyVertexOfTheShellIsOnTheSurfaceTheCarDrivesOn() {
        ArenaMesh mesh = ArenaMesh.build();
        assertTrue(mesh.shell.vertexCount > 10000, "shell vertices " + mesh.shell.vertexCount);
        assertTrue(worstDistance(mesh.shell) < 0.08D, "shell " + worstDistance(mesh.shell));
        assertTrue(worstDistance(mesh.glow) < 0.1D, "glow " + worstDistance(mesh.glow));
        assertEquals(0.02D, worstDistance(mesh.floor), 1e-4D);
    }

    @Test
    void theNormalsOfTheShellPointIntoTheArenaLikeThePhysicsNormals() {
        ArenaMesh mesh = ArenaMesh.build();
        double[] n = new double[3];
        int checked = 0;
        for (int i = 0; i < mesh.shell.vertexCount; i += 37) {
            int o = i * ArenaMesh.FLOATS_PER_VERTEX;
            double x = mesh.shell.data[o];
            double y = mesh.shell.data[o + 1];
            double z = mesh.shell.data[o + 2];
            Arena.normal(x, y, z, n);
            double dot = n[0] * mesh.shell.data[o + 3] + n[1] * mesh.shell.data[o + 4] + n[2] * mesh.shell.data[o + 5];
            // (the physics normal is a gradient: it can differ in the thin seams where two surfaces meet, never by much)
            assertTrue(dot > 0.9D, "normal mismatch " + dot + " at " + x + ", " + y + ", " + z);
            checked++;
        }
        assertTrue(checked > 100);
    }

    @Test
    void theMeshIsSmallEnoughToDrawEveryFrame() {
        ArenaMesh mesh = ArenaMesh.build();
        assertTrue(mesh.shell.vertexCount + mesh.glow.vertexCount < 80000, "vertices " + (mesh.shell.vertexCount + mesh.glow.vertexCount));
    }
}
