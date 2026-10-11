package peroxicore.ponder.api;

import static mindustry.content.Blocks.*;

import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.gen.*;

public class PonderRegistryJava {
  // just for test
  public static void init() {
    PonderJava.register(
        Blocks.duo,
        scene -> {
          scene.keyframes(
              kf -> {
                kf.speed(1f);
                kf.world(
                    13,
                    11,
                    w -> {
                      w.fillFloor(Blocks.rhyolite);
                      w.fillAround(4, 3, 3, Blocks.rhyoliteVent);
                      w.fillAround(4, 7, 3, Blocks.rhyoliteVent);
                      w.focus(8, 5, 5);
                    });
                kf.frame(
                    50f,
                    f -> {
                      Weathers.rain.create();
                      f.build(8, 5, Blocks.electrolyzer);
                      f.label(9, 5, "电解机", 4f);
                      UnitTypes.flare.spawn(Team.get(2), 24f, 40f);
                    });
              });
        });
  }
}
