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
                      w.at(4, 3).around3().fill(Blocks.rhyoliteVent);
                      w.at(4, 7).around3().fill(Blocks.rhyoliteVent);
                      w.at(8, 5).focus(5);
                    });
                kf.frame(
                    50f,
                    f -> {
                      Weathers.rain.create();
                      f.at(8, 5).build(Blocks.electrolyzer);
                      f.at(9, 5).label("电解机", 4f);
                      UnitTypes.flare.spawn(Team.get(2), 24f, 40f);
                    });
              });
        });
  }
}
