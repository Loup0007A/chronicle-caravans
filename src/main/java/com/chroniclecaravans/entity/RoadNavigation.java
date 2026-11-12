package com.chroniclecaravans.entity;

import com.chroniclecaravans.CCConfig;
import com.chroniclecaravans.ChronicleCaravans;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** Ground navigation that makes every block off the road more expensive, so paths stick to roads. */
public class RoadNavigation extends GroundPathNavigation {
    public RoadNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new RoadEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    static class RoadEvaluator extends WalkNodeEvaluator {
        private float penalty;
        private final BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

        @Override
        public void prepare(PathNavigationRegion region, Mob mob) {
            super.prepare(region, mob);
            penalty = CCConfig.ROADS_ENABLED.get() ? CCConfig.OFF_ROAD_PENALTY.get().floatValue() : 0F;
        }

        @Override
        protected Node findAcceptedNode(int x, int y, int z, int verticalDeltaLimit, double nodeFloorLevel, Direction direction, PathType pathType) {
            Node node = super.findAcceptedNode(x, y, z, verticalDeltaLimit, nodeFloorLevel, direction, pathType);
            if (node != null && penalty > 0F && node.costMalus >= 0F && node.costMalus < penalty) {
                below.set(node.x, node.y - 1, node.z);
                if (!this.currentContext.getBlockState(below).is(ChronicleCaravans.ROADS)) node.costMalus = penalty;
            }
            return node;
        }
    }
}
