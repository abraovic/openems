import { formatNumber } from '@angular/common';
import { Component, Input, OnChanges, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { BaseChartDirective } from 'ng2-charts/ng2-charts';
import { QueryHistoricTimeseriesDataResponse } from '../../../../shared/jsonrpc/response/queryHistoricTimeseriesDataResponse';
import { ChannelAddress, Edge, Service, Utils } from '../../../../shared/shared';
import { AbstractHistoryChart } from '../../abstracthistorychart';
import { ChartOptions, Data, Dataset, DEFAULT_TIME_CHART_OPTIONS, EMPTY_DATASET, TooltipItem } from './../shared';

@Component({
  selector: 'socchart',
  templateUrl: './socchart.component.html'
})
export class SocChartComponent extends AbstractHistoryChart implements OnInit, OnChanges {

  @ViewChild('socChart') protected chart: BaseChartDirective;

  @Input() private fromDate: Date;
  @Input() private toDate: Date;

  ngOnChanges() {
    this.updateChart();
  };

  constructor(
    protected service: Service,
    private route: ActivatedRoute,
    private translate: TranslateService
  ) {
    super(service);
  }

  public loading: boolean = true;

  protected labels: Date[] = [];
  protected datasets: Dataset[] = EMPTY_DATASET;
  protected options: ChartOptions;
  protected colors = [{
    backgroundColor: 'rgba(0,152,70,0.05)',
    borderColor: 'rgba(0,152,70,1)',
  }, {
    backgroundColor: 'rgba(0,152,204,0.05)',
    borderColor: 'rgba(0,152,204,1)'
  }, {
    backgroundColor: 'rgba(107,207,0,0.05)',
    borderColor: 'rgba(107,207,0,1)'
  }, {
    backgroundColor: 'rgba(224,232,17,0.05)',
    borderColor: 'rgba(224,232,17,1)'
  }];

  ngOnInit() {
    this.service.setCurrentEdge(this.route);
    let options = <ChartOptions>Utils.deepCopy(DEFAULT_TIME_CHART_OPTIONS);
    options.scales.yAxes[0].scaleLabel.labelString = this.translate.instant('General.Percentage');
    options.tooltips.callbacks.label = function (tooltipItem: TooltipItem, data: Data) {
      let label = data.datasets[tooltipItem.datasetIndex].label;
      let value = tooltipItem.yLabel;
      if (label == this.grid) {
        if (value < 0) {
          value *= -1;
          label = this.gridBuy;
        } else {
          label = this.gridSell;
        }
      }
      return label + ": " + formatNumber(value, 'de', '1.0-0') + " %"; // TODO get locale dynamically
    }
    options.scales.yAxes[0].ticks.max = 100;
    this.options = options;
  }

  private updateChart() {
    this.loading = true;
    this.queryHistoricTimeseriesData(this.fromDate, this.toDate).then(response => {
      let result = (response as QueryHistoricTimeseriesDataResponse).result;

      // convert labels
      let labels: Date[] = [];
      for (let timestamp of result.timestamps) {
        labels.push(new Date(timestamp));
      }
      this.labels = labels;

      // show Component-ID if there is more than one Channel
      let showComponentId = Object.keys(result.data).length > 1 ? true : false;

      // convert datasets
      let datasets = [];
      for (let channel in result.data) {
        let address = ChannelAddress.fromString(channel);
        let data = result.data[channel].map(value => {
          if (value == null) {
            return null
          } else if (value > 100 || value < 0) {
            return null;
          } else {
            return value;
          }
        });
        datasets.push({
          label: this.translate.instant('General.Soc') + (showComponentId ? ' (' + address.componentId + ')' : ''),
          data: data
        });
      }
      this.datasets = datasets;

      this.loading = false;

    }).catch(reason => {
      console.error(reason); // TODO error message
      this.initializeChart();
      return;
    });
  }

  protected getChannelAddresses(edge: Edge): Promise<ChannelAddress[]> {
    return new Promise((resolve, reject) => {
      if (edge.isVersionAtLeast('2018.8')) {
        resolve([new ChannelAddress('_sum', 'EssSoc')]);

      } else {
        // TODO: remove after full migration
        this.service.getConfig().then(config => {
          // get 'Soc'-Channel of all 'EssNatures'
          let channeladdresses = [];
          for (let componentId of config.getComponentsImplementingNature("EssNature")) {
            channeladdresses.push(new ChannelAddress(componentId, 'Soc'));
          }
          resolve(channeladdresses);
        }).catch(reason => reject(reason));
      }
    });
  }

  private initializeChart() {
    this.datasets = EMPTY_DATASET;
    this.labels = [];
    this.loading = false;
  }

}